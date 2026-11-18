package com.fahri.product.seeder;

import com.fahri.product.entity.Product;
import com.fahri.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * High-performance Java data seeder for Product Service.
 * Supports dynamic generation of catalog products with a strictly enforced upper boundary (1 to 500 products).
 * Pre-warms the in-memory Redis Data Grid with product stock keys for low-latency stress testing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductDataSeeder implements CommandLineRunner {

    public static final int MIN_SEEDER_COUNT = 1;
    public static final int MAX_SEEDER_COUNT = 500;
    private static final String REDIS_STOCK_PREFIX = "product:stock:";

    private final ProductRepository productRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final com.fahri.product.service.ProductSearchService productSearchService;

    @Value("${app.seeder.product.enabled:true}")
    private boolean seederEnabled;

    @Value("${app.seeder.product.count:100}")
    private int defaultStartupCount;

    public record ProductSeedSpec(
            String name,
            String category,
            BigDecimal price,
            int stock,
            String description
    ) {}

    @Override
    public void run(String... args) {
        // Clear any stale cached Spring Cache responses on startup
        try {
            Set<String> staleCacheKeys = redisTemplate.keys("products::*");
            if (staleCacheKeys != null && !staleCacheKeys.isEmpty()) {
                redisTemplate.delete(staleCacheKeys);
                log.info("Cleared {} stale product Spring Cache keys on startup.", staleCacheKeys.size());
            }
        } catch (Exception ex) {
            log.debug("Startup Redis cache cleanup skipped: {}", ex.getMessage());
        }

        if (!seederEnabled) {
            log.info("ProductDataSeeder is disabled via application configuration.");
            return;
        }

        int targetStartupCount = Math.min(Math.max(defaultStartupCount, MIN_SEEDER_COUNT), MAX_SEEDER_COUNT);
        long existingCount = productRepository.count();

        if (existingCount < targetStartupCount) {
            log.info("Current product count is {} (< {}). Executing dynamic Java data seeder...", existingCount, targetStartupCount);
            seedDynamic(targetStartupCount, false);
        } else {
            log.info("Database already contains {} products. Skipping automatic seeder execution.", existingCount);
        }
    }

    /**
     * Seeds a dynamic number of products into the database and pre-warms Redis cache.
     *
     * @param count The number of products to generate (strictly bounded between 1 and 500).
     * @param force Whether to force re-seeding even if database already has >= count records.
     * @return Execution summary map with analytics and statistics.
     * @throws IllegalArgumentException if count is outside the valid range [1, 500].
     */
    @Transactional
    public Map<String, Object> seedDynamic(int count, boolean force) {
        // Enforce boundary constraint: maximum 500, minimum 1
        validateSeederCount(count);

        long startTime = System.currentTimeMillis();
        long existingCount = productRepository.count();

        if (existingCount >= count && !force) {
            log.info("Seeder skipped. Database already has {} products (>= requested {}). Set force=true to override.",
                    existingCount, count);
            return Map.of(
                    "status", "SKIPPED",
                    "message", "Database already contains sufficient products for testing (current: " + existingCount + ")",
                    "requestedCount", count,
                    "currentDatabaseCount", existingCount,
                    "maxAllowedCount", MAX_SEEDER_COUNT
            );
        }

        log.info("Dynamically generating {} performance test products (Max limit: {})...", count, MAX_SEEDER_COUNT);

        List<ProductSeedSpec> baseSpecs = getPerformanceProductSpecs();

        // Java Stream: Procedurally generate exactly `count` products with varied SKU tags, prices, and stock
        List<Product> productsToSave = IntStream.range(0, count)
                .mapToObj(index -> {
                    ProductSeedSpec base = baseSpecs.get(index % baseSpecs.size());
                    int cycle = index / baseSpecs.size();

                    String productName = (cycle == 0)
                            ? base.name()
                            : base.name() + " [Batch #" + (cycle + 1) + " - SKU-" + String.format("%03d", index + 1) + "]";

                    // Slight price variation across batches for realistic distribution (±5% per cycle)
                    BigDecimal price = (cycle == 0)
                            ? base.price()
                            : base.price().multiply(BigDecimal.valueOf(1.0 + ((index % 5) * 0.03)))
                                    .setScale(2, RoundingMode.HALF_UP);

                    // Varied stock for realistic flash sale scenarios (between 30 and 400)
                    int stock = (cycle == 0)
                            ? base.stock()
                            : Math.min(base.stock() + ((index % 8) * 15), 500);

                    return Product.builder()
                            .name(productName)
                            .category(base.category())
                            .price(price)
                            .stock(stock)
                            .description(base.description())
                            .build();
                })
                .collect(Collectors.toList());

        // Batch persist to PostgreSQL
        List<Product> savedProducts = productRepository.saveAll(productsToSave);

        // Batch index products into Elasticsearch search catalog
        productSearchService.indexAllProducts(savedProducts);

        // Java Stream: Pre-warm Redis In-Memory Data Grid with product stock keys
        long redisWarmedCount = savedProducts.stream()
                .filter(p -> p.getId() != null)
                .peek(product -> {
                    try {
                        redisTemplate.opsForValue().set(REDIS_STOCK_PREFIX + product.getId(), product.getStock());
                    } catch (Exception ex) {
                        log.debug("Redis stock pre-warming skipped for product id {}: {}", product.getId(), ex.getMessage());
                    }
                })
                .count();

        // Java Stream reductions and analytics
        Map<String, Long> categoryDistribution = savedProducts.stream()
                .collect(Collectors.groupingBy(Product::getCategory, Collectors.counting()));

        int totalInventoryStock = savedProducts.stream()
                .mapToInt(Product::getStock)
                .sum();

        BigDecimal averagePrice = savedProducts.stream()
                .map(Product::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(savedProducts.size()), 2, RoundingMode.HALF_UP);

        BigDecimal minPrice = savedProducts.stream()
                .map(Product::getPrice)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BigDecimal maxPrice = savedProducts.stream()
                .map(Product::getPrice)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        long executionTimeMs = System.currentTimeMillis() - startTime;
        long totalCountAfter = productRepository.count();

        log.info("==========================================================================");
        log.info("Dynamic seeder finished! Persisted {} products in {} ms", savedProducts.size(), executionTimeMs);
        log.info("Total products in database: {}", totalCountAfter);
        log.info("Total inventory stock count: {} units", totalInventoryStock);
        log.info("Price range: Rp {} - Rp {} (Average: Rp {})", minPrice, maxPrice, averagePrice);
        log.info("Redis cache keys pre-warmed: {}", redisWarmedCount);
        log.info("Category distribution: {}", categoryDistribution);
        log.info("==========================================================================");

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("status", "SUCCESS");
        summary.put("seededCount", savedProducts.size());
        summary.put("requestedCount", count);
        summary.put("maxAllowedCount", MAX_SEEDER_COUNT);
        summary.put("totalProductsInDatabase", totalCountAfter);
        summary.put("totalInventoryStock", totalInventoryStock);
        summary.put("averagePrice", averagePrice);
        summary.put("minPrice", minPrice);
        summary.put("maxPrice", maxPrice);
        summary.put("redisKeysWarmed", redisWarmedCount);
        summary.put("elasticsearchIndexed", savedProducts.size());
        summary.put("executionTimeMs", executionTimeMs);
        summary.put("categoryDistribution", categoryDistribution);

        return summary;
    }

    /**
     * Backward-compatible convenience method for default 100-item seeder.
     */
    public Map<String, Object> seedPerformanceProducts(boolean force) {
        return seedDynamic(100, force);
    }

    private void validateSeederCount(int count) {
        if (count < MIN_SEEDER_COUNT || count > MAX_SEEDER_COUNT) {
            log.error("Invalid seeder count requested: {}. Must be between {} and {}.",
                    count, MIN_SEEDER_COUNT, MAX_SEEDER_COUNT);
            throw new IllegalArgumentException(String.format(
                    "Seeder count must be between %d and %d. Requested count: %d",
                    MIN_SEEDER_COUNT, MAX_SEEDER_COUNT, count));
        }
    }

    public List<ProductSeedSpec> getPerformanceProductSpecs() {
        return List.of(
                // 1. Smartphone (10 items)
                new ProductSeedSpec("Samsung Galaxy S24+ 256GB - Onyx Black", "Smartphone", new BigDecimal("16999000.00"), 150, "Flagship AI smartphone with Dynamic AMOLED 2X and Exynos 2400."),
                new ProductSeedSpec("Apple iPhone 15 128GB - Blue", "Smartphone", new BigDecimal("15499000.00"), 120, "Advanced dual-camera system, Dynamic Island, and A16 Bionic chip."),
                new ProductSeedSpec("Google Pixel 8 Pro 128GB - Obsidian", "Smartphone", new BigDecimal("14899000.00"), 80, "Google Tensor G3, best-in-class camera system, and 7 years of OS updates."),
                new ProductSeedSpec("OnePlus 12 512GB - Silky Black", "Smartphone", new BigDecimal("12999000.00"), 100, "Snapdragon 8 Gen 3, 4th Gen Hasselblad Camera, and 100W SUPERVOOC charging."),
                new ProductSeedSpec("Xiaomi 14 Ultra 512GB - White", "Smartphone", new BigDecimal("17499000.00"), 60, "Leica Quad Camera with 1-inch sensor and Snapdragon 8 Gen 3."),
                new ProductSeedSpec("Asus ROG Phone 8 Pro 512GB - Phantom Black", "Smartphone", new BigDecimal("18999000.00"), 50, "Ultimate gaming phone with AniMe Vision, Snapdragon 8 Gen 3, and 165Hz AMOLED."),
                new ProductSeedSpec("Vivo X100 Pro 512GB - Asteroid Black", "Smartphone", new BigDecimal("15999000.00"), 70, "ZEISS APO Telephoto camera with Dimensity 9300 and V3 imaging chip."),
                new ProductSeedSpec("Oppo Find X7 Ultra 256GB - Ocean Blue", "Smartphone", new BigDecimal("16499000.00"), 65, "World first quad main camera with dual periscope telephoto lenses."),
                new ProductSeedSpec("Nothing Phone (2) 256GB - Dark Grey", "Smartphone", new BigDecimal("9999000.00"), 110, "Iconic Glyph Interface, Snapdragon 8+ Gen 1, and 50MP dual camera."),
                new ProductSeedSpec("Poco F6 Pro 512GB - Moonlight Silver", "Smartphone", new BigDecimal("6499000.00"), 250, "Snapdragon 8 Gen 2 flagship killer with 120W HyperCharge and WQHD+ AMOLED."),

                // 2. Laptop (10 items)
                new ProductSeedSpec("Apple MacBook Pro 14-inch M3 Pro (18GB, 512GB) - Space Black", "Laptop", new BigDecimal("31999000.00"), 40, "Liquid Retina XDR display, up to 18 hours battery life, and pro performance."),
                new ProductSeedSpec("Dell XPS 15 9530 Intel Core i9 (32GB, 1TB SSD, RTX 4070)", "Laptop", new BigDecimal("34500000.00"), 30, "Stunning 3.5K OLED touchscreen with premium aluminum chassis."),
                new ProductSeedSpec("Lenovo ThinkPad X1 Carbon Gen 12 (16GB, 1TB SSD)", "Laptop", new BigDecimal("28999000.00"), 50, "Ultralight business flagship with Intel Core Ultra 7 and legendary keyboard."),
                new ProductSeedSpec("ASUS Zenbook 14 OLED Intel Core Ultra 7 (16GB, 1TB)", "Laptop", new BigDecimal("19999000.00"), 75, "Ultraportable design with Intel Arc graphics and 120Hz 3K OLED display."),
                new ProductSeedSpec("HP Spectre x360 14 2-in-1 OLED (16GB, 1TB SSD)", "Laptop", new BigDecimal("24499000.00"), 45, "Premium convertible laptop with 9MP AI camera and Intel Evo platform."),
                new ProductSeedSpec("Acer Predator Helios 16 (Intel i7, RTX 4070, 16GB, 1TB)", "Laptop", new BigDecimal("26999000.00"), 60, "High-performance gaming laptop with 240Hz WQXGA mini-LED display."),
                new ProductSeedSpec("Apple MacBook Air 15-inch M3 (16GB, 512GB) - Starlight", "Laptop", new BigDecimal("22999000.00"), 80, "Spacious 15.3-inch Liquid Retina display with whisper-quiet fanless design."),
                new ProductSeedSpec("Lenovo Legion Pro 5i (Intel i7, RTX 4060, 16GB, 1TB)", "Laptop", new BigDecimal("23499000.00"), 70, "AI-tuned gaming performance with Coldfront 5.0 thermal technology."),
                new ProductSeedSpec("MSI Stealth 14 Studio (Intel i7, RTX 4060, 16GB, 1TB)", "Laptop", new BigDecimal("25999000.00"), 40, "Ultra-thin magnesium-aluminum gaming and creator powerhouse."),
                new ProductSeedSpec("ASUS TUF Gaming A15 (AMD Ryzen 7, RTX 4060, 16GB, 512GB)", "Laptop", new BigDecimal("16999000.00"), 100, "Military-grade durability gaming laptop with 144Hz FHD display."),

                // 3. Audio (10 items)
                new ProductSeedSpec("Apple AirPods Pro (2nd Generation) with MagSafe USB-C", "Audio", new BigDecimal("3899000.00"), 200, "Active Noise Cancellation, Transparency mode, and Personalized Spatial Audio."),
                new ProductSeedSpec("Bose QuietComfort Ultra Wireless Noise Cancelling Headphones", "Audio", new BigDecimal("5999000.00"), 90, "Breakthrough spatialized audio with world-class active noise cancellation."),
                new ProductSeedSpec("Sennheiser Momentum 4 Wireless Headphones", "Audio", new BigDecimal("5499000.00"), 85, "Audiophile-inspired 42mm transducer system with 60-hour battery life."),
                new ProductSeedSpec("Sony WF-1000XM5 True Wireless Noise Canceling Earbuds", "Audio", new BigDecimal("3999000.00"), 150, "Dual processors for unmatched noise cancellation and high-resolution sound."),
                new ProductSeedSpec("Marshall Stanmore III Bluetooth Home Speaker", "Audio", new BigDecimal("6299000.00"), 70, "Iconic vintage design with re-engineered wider stereo soundstage."),
                new ProductSeedSpec("Sonos Era 300 Spatial Audio Smart Speaker", "Audio", new BigDecimal("7999000.00"), 60, "Revolutionary acoustic design featuring six drivers for Dolby Atmos sound."),
                new ProductSeedSpec("JBL Charge 5 Portable Waterproof Bluetooth Speaker", "Audio", new BigDecimal("2499000.00"), 220, "Optimized long-excursion driver and dual JBL bass radiators with powerbank feature."),
                new ProductSeedSpec("Shure SM7B Cardioid Dynamic Studio Vocal Microphone", "Audio", new BigDecimal("6999000.00"), 80, "Legendary warm, smooth vocal microphone for broadcasting, podcasts, and recording."),
                new ProductSeedSpec("Audio-Technica ATH-M50x Professional Studio Monitor Headphones", "Audio", new BigDecimal("2299000.00"), 180, "Critically acclaimed sonic performance with 45mm large-aperture drivers."),
                new ProductSeedSpec("Bang & Olufsen Beosound Explore Outdoor Speaker", "Audio", new BigDecimal("3799000.00"), 95, "Tough and hard-anodized aluminum shell with True360 omnidirectional sound."),

                // 4. Gaming (10 items)
                new ProductSeedSpec("Valve Steam Deck OLED 512GB Handheld Gaming Console", "Gaming", new BigDecimal("9999000.00"), 85, "7.4-inch HDR OLED display with 90Hz refresh rate and extended battery life."),
                new ProductSeedSpec("ASUS ROG Ally Z1 Extreme 512GB Gaming Handheld", "Gaming", new BigDecimal("10999000.00"), 75, "Full HD 120Hz display with Windows 11 and AMD Ryzen Z1 Extreme processor."),
                new ProductSeedSpec("Xbox Series X 1TB Gaming Console - Black", "Gaming", new BigDecimal("8499000.00"), 90, "Fastest, most powerful Xbox ever with 12 teraflops of raw graphic processing power."),
                new ProductSeedSpec("Sony PlayStation VR2 Horizon Call of the Mountain Bundle", "Gaming", new BigDecimal("9499000.00"), 45, "4K HDR visuals, eye tracking, and Tempest 3D AudioTech for immersive VR."),
                new ProductSeedSpec("Meta Quest 3 128GB Advanced All-in-One VR Headset", "Gaming", new BigDecimal("8999000.00"), 65, "Breakthrough mixed reality with 4K+ Infinite Display and Touch Plus controllers."),
                new ProductSeedSpec("Logitech G Cloud Gaming Handheld", "Gaming", new BigDecimal("5499000.00"), 100, "7-inch 1080p gaming handheld precision-engineered for Xbox Cloud and GeForce NOW."),
                new ProductSeedSpec("Secretlab TITAN Evo 2022 Stealth Gaming Chair", "Gaming", new BigDecimal("7899000.00"), 40, "Ergonomic gaming chair with 4-way L-ADAPT lumbar support and cold-cure foam."),
                new ProductSeedSpec("SteelSeries Arctis Nova Pro Wireless Gaming Headset", "Gaming", new BigDecimal("5899000.00"), 80, "Multi-system connect with active noise cancellation and infinity power battery system."),
                new ProductSeedSpec("Razer Kitsune All-Button Optical Arcade Controller", "Gaming", new BigDecimal("4999000.00"), 50, "Precise quad-movement button layout with Razer Low-Profile Optical Switches."),
                new ProductSeedSpec("Elgato Stream Deck MK.2 15 LCD Keys", "Gaming", new BigDecimal("2499000.00"), 160, "Customizable tactile keys to trigger actions, launch media, and switch scenes."),

                // 5. Wearable (10 items)
                new ProductSeedSpec("Samsung Galaxy Watch 6 Classic 47mm LTE", "Wearable", new BigDecimal("5499000.00"), 110, "Rotating bezel design with advanced sleep coaching and ECG heart monitoring."),
                new ProductSeedSpec("Apple Watch Series 9 GPS 45mm - Midnight Aluminum", "Wearable", new BigDecimal("7499000.00"), 130, "S9 SiP chip, double tap gesture, and brighter Always-On Retina display."),
                new ProductSeedSpec("Garmin Fenix 7 Pro Sapphire Solar Multi-Sport GPS Watch", "Wearable", new BigDecimal("14999000.00"), 40, "Solar charging lens, built-in LED flashlight, and advanced endurance metrics."),
                new ProductSeedSpec("Garmin Forerunner 965 AMOLED Premium Running Watch", "Wearable", new BigDecimal("9999000.00"), 60, "Titanium bezel with brilliant AMOLED touchscreen and full-color built-in maps."),
                new ProductSeedSpec("Oura Ring Gen 3 Horizon Smart Health Tracker - Stealth", "Wearable", new BigDecimal("5899000.00"), 80, "Discreet biometric ring tracking sleep stages, readiness, and heart rate variability."),
                new ProductSeedSpec("Whoop 4.0 12-Month Fitness & Health Tracker Band", "Wearable", new BigDecimal("4299000.00"), 90, "Continuous physiological monitoring with recovery metrics and screenless design."),
                new ProductSeedSpec("Huawei Watch GT 4 46mm Smartwatch - Stainless Steel", "Wearable", new BigDecimal("3499000.00"), 140, "Geometric aesthetic with up to 14 days battery life and TruSeen 5.5+ monitoring."),
                new ProductSeedSpec("Fitbit Charge 6 Fitness & Health Tracker", "Wearable", new BigDecimal("2499000.00"), 170, "Built-in GPS, Google apps integration, and 40+ exercise modes."),
                new ProductSeedSpec("Amazfit T-Rex 2 Rugged Outdoor GPS Smartwatch", "Wearable", new BigDecimal("2999000.00"), 120, "15 military certifications with dual-band 5-satellite positioning and 24-day battery."),
                new ProductSeedSpec("Withings ScanWatch 2 Hybrid Smartwatch", "Wearable", new BigDecimal("5999000.00"), 50, "Clinically validated hybrid smartwatch with ECG, SpO2, and baseline body temperature."),

                // 6. Camera (10 items)
                new ProductSeedSpec("Sony Alpha a7 IV Full-Frame Mirrorless Camera Body", "Camera", new BigDecimal("34999000.00"), 25, "33MP Exmor R CMOS sensor, 4K 60p video, and real-time Eye AF for photo and video."),
                new ProductSeedSpec("Canon EOS R6 Mark II Mirrorless Camera Body", "Camera", new BigDecimal("36500000.00"), 20, "24.2MP full-frame CMOS sensor, 40 fps electronic shutter, and 6K oversampled 4K."),
                new ProductSeedSpec("Fujifilm X-T5 Mirrorless Camera Body - Silver", "Camera", new BigDecimal("26499000.00"), 35, "40.2MP X-Trans CMOS 5 HR sensor with 5-axis 7-stop in-body image stabilization."),
                new ProductSeedSpec("Sony FX30 Cinema Line Super 35 Camera Body", "Camera", new BigDecimal("29999000.00"), 30, "Cinema-standard 4K Super 35 sensor with dual base ISO and XLR handle unit support."),
                new ProductSeedSpec("DJI Osmo Pocket 3 Creator Combo 4K Handheld Gimbal Camera", "Camera", new BigDecimal("10499000.00"), 90, "1-inch CMOS sensor, 4K 120fps recording, and 2-inch rotatable OLED touchscreen."),
                new ProductSeedSpec("GoPro HERO12 Black Action Camera 5.3K", "Camera", new BigDecimal("6499000.00"), 130, "HyperSmooth 6.0 stabilization, HDR 5.3K video, and extended battery endurance."),
                new ProductSeedSpec("Insta360 X4 8K 360-Degree Action Camera", "Camera", new BigDecimal("8999000.00"), 75, "8K 360 capture with FlowState stabilization and invisible selfie stick effect."),
                new ProductSeedSpec("DJI RS 3 Pro 3-Axis Camera Gimbal Stabilizer", "Camera", new BigDecimal("13999000.00"), 45, "Automated axis locks, carbon fiber arms, and 4.5kg tested payload capacity."),
                new ProductSeedSpec("Sony FE 24-70mm f/2.8 GM II Lens", "Camera", new BigDecimal("31999000.00"), 20, "Lightest and sharpest standard zoom G Master lens with 4 XD linear motors."),
                new ProductSeedSpec("Sigma 24-70mm f/2.8 DG DN Art Lens for Sony E-Mount", "Camera", new BigDecimal("16499000.00"), 50, "Outstanding optical performance with constant f/2.8 aperture and dust-proof design."),

                // 7. Smart Home (10 items)
                new ProductSeedSpec("Roborock S8 Pro Ultra Robot Vacuum and Mop with RockDock", "Smart Home", new BigDecimal("17999000.00"), 35, "6000Pa suction power with self-washing, self-drying, and self-emptying dock."),
                new ProductSeedSpec("Dyson V15 Detect Absolute Cordless Vacuum Cleaner", "Smart Home", new BigDecimal("13499000.00"), 45, "Laser reveals microscopic dust with piezo sensor calculating particle count."),
                new ProductSeedSpec("Dyson Purifier Hot+Cool Formaldehyde Air Purifier HP09", "Smart Home", new BigDecimal("12999000.00"), 40, "HEPA H13 filtration capturing 99.95% of ultrafine particles and destroying VOCs."),
                new ProductSeedSpec("Philips Hue White & Color Ambiance Starter Kit E27", "Smart Home", new BigDecimal("2999000.00"), 120, "16 million colors with Zigbee hub bridge for smart home lighting automation."),
                new ProductSeedSpec("Ecobee Premium Smart Thermostat with Voice Control", "Smart Home", new BigDecimal("3999000.00"), 70, "Built-in air quality monitor, smart sensor, and Apple HomeKit / Alexa compatibility."),
                new ProductSeedSpec("Yale Assure Lock 2 Touch Keyless Smart Door Lock", "Smart Home", new BigDecimal("3699000.00"), 85, "Biometric fingerprint scanner, keypad, and Wi-Fi remote access."),
                new ProductSeedSpec("Xiaomi Smart Air Purifier 4 Pro High-Efficiency Filter", "Smart Home", new BigDecimal("3199000.00"), 160, "500m3/h CADR suitable for large rooms with OLED display and low-noise mode."),
                new ProductSeedSpec("Breville Barista Touch Impress Espresso Coffee Machine", "Smart Home", new BigDecimal("19999000.00"), 25, "Assisted tamping and intelligent dosing with automated microfoam milk texturing."),
                new ProductSeedSpec("DeLonghi Magnifica S Fully Automatic Coffee Machine", "Smart Home", new BigDecimal("9999000.00"), 50, "Integrated burr grinder with twin-shot espresso brewing and manual frother."),
                new ProductSeedSpec("Anker Eufy RoboVac X8 Hybrid Robot Vacuum and Mop", "Smart Home", new BigDecimal("6499000.00"), 75, "Twin-turbine technology generating 2x 2000Pa suction with laser LiDAR navigation."),

                // 8. Accessories (10 items)
                new ProductSeedSpec("Logitech MX Master 3S Wireless Performance Mouse", "Accessories", new BigDecimal("1699000.00"), 300, "Quiet clicks with 8000 DPI Darkfield sensor and MagSpeed electromagnetic scroll."),
                new ProductSeedSpec("Logitech MX Keys S Advanced Wireless Illuminated Keyboard", "Accessories", new BigDecimal("1799000.00"), 250, "Smart illumination with spherically dished keys and fluid typing response."),
                new ProductSeedSpec("Keychron Q1 Pro Wireless Custom Mechanical Keyboard - Barebone", "Accessories", new BigDecimal("2899000.00"), 100, "Full aluminum CNC body with QMK/VIA programmable knobs and South-facing RGB."),
                new ProductSeedSpec("Razer DeathAdder V3 Pro Ultra-Lightweight Wireless Gaming Mouse", "Accessories", new BigDecimal("2399000.00"), 180, "63g ultra-lightweight ergonomic shape with Focus Pro 30K Optical Sensor."),
                new ProductSeedSpec("SteelSeries Apex Pro TKL Wireless Mechanical Keyboard", "Accessories", new BigDecimal("4199000.00"), 90, "OmniPoint 2.0 adjustable hypermagnetic switches with OLED smart display."),
                new ProductSeedSpec("Apple Magic Trackpad - Black Multi-Touch Surface", "Accessories", new BigDecimal("2299000.00"), 160, "Wireless and rechargeable trackpad with full suite of macOS Multi-Touch gestures."),
                new ProductSeedSpec("Wacom Intuos Pro Medium Graphics Tablet", "Accessories", new BigDecimal("5499000.00"), 65, "Pro Pen 2 with 8192 levels of pressure sensitivity and multi-touch surface."),
                new ProductSeedSpec("CalDigit TS4 Thunderbolt 4 18-Port Docking Station", "Accessories", new BigDecimal("6499000.00"), 60, "98W host charging, dual 6K display support, and 2.5 Gigabit Ethernet port."),
                new ProductSeedSpec("Anker 737 Power Bank (PowerCore 24K, 140W Output)", "Accessories", new BigDecimal("1999000.00"), 280, "Smart digital display with Power Delivery 3.1 ultra-fast charging."),
                new ProductSeedSpec("Ugreen Nexode 300W 5-Port GaN Desktop Fast Charger", "Accessories", new BigDecimal("2199000.00"), 200, "High-power GaN III desktop fast charger capable of charging 3 laptops simultaneously."),

                // 9. Storage (10 items)
                new ProductSeedSpec("Samsung 990 PRO 2TB PCIe 4.0 NVMe M.2 Internal SSD", "Storage", new BigDecimal("3199000.00"), 220, "Sequential read speeds up to 7450 MB/s for hardcore gaming and heavy workloads."),
                new ProductSeedSpec("WD_BLACK SN850X 4TB NVMe Internal Gaming SSD with Heatsink", "Storage", new BigDecimal("5999000.00"), 90, "Top-tier PCIe Gen4 performance up to 7300 MB/s with Game Mode 2.0."),
                new ProductSeedSpec("Crucial T700 2TB PCIe Gen5 NVMe M.2 Gaming SSD", "Storage", new BigDecimal("4799000.00"), 70, "Blistering speeds up to 12400 MB/s with aluminum and nickel-plated copper heatsink."),
                new ProductSeedSpec("SanDisk Extreme PRO Portable SSD 2TB USB 3.2 Gen 2x2", "Storage", new BigDecimal("3499000.00"), 175, "Rugged NVMe performance up to 2000 MB/s with IP65 dust and water resistance."),
                new ProductSeedSpec("Samsung T9 Portable SSD 4TB High-Speed USB 3.2", "Storage", new BigDecimal("6299000.00"), 80, "Turbocharged 2000 MB/s transfer speeds with thermal guard and drop protection."),
                new ProductSeedSpec("Synology DiskStation DS923+ 4-Bay NAS Enclosure", "Storage", new BigDecimal("9999000.00"), 45, "AMD Ryzen R1600 dual-core processor with 10GbE network card expansion."),
                new ProductSeedSpec("QNAP TS-464-8G 4-Bay High-Performance NAS", "Storage", new BigDecimal("10499000.00"), 40, "Intel Celeron N5095 quad-core CPU with dual 2.5GbE ports and HDMI 4K output."),
                new ProductSeedSpec("Seagate IronWolf Pro 16TB Enterprise NAS Internal HDD", "Storage", new BigDecimal("5299000.00"), 85, "7200 RPM CMR enterprise-class drive with AgileArray and 300TB/year workload."),
                new ProductSeedSpec("Corsair Dominator Titanium DDR5 RAM 64GB (2x32GB) 6000MHz", "Storage", new BigDecimal("4699000.00"), 65, "Custom high-frequency memory modules with patented DHX cooling and RGB lighting."),
                new ProductSeedSpec("G.Skill Trident Z5 RGB Series DDR5 32GB (2x16GB) 7200MHz", "Storage", new BigDecimal("2999000.00"), 110, "Extreme overclocking performance with sleek dual-texture aluminum heatspreader."),

                // 10. Networking & Displays (10 items)
                new ProductSeedSpec("ASUS ROG Rapture GT-AXE16000 Quad-Band WiFi 6E Gaming Router", "Networking", new BigDecimal("9999000.00"), 45, "World first quad-band WiFi 6E gaming router with dual 10G ports."),
                new ProductSeedSpec("Netgear Nighthawk RS700S Tri-Band WiFi 7 Gaming Router", "Networking", new BigDecimal("11499000.00"), 35, "Next-generation WiFi 7 speeds up to 19Gbps with 10Gbps internet port."),
                new ProductSeedSpec("Ubiquiti UniFi Dream Machine Special Edition (UDM-SE)", "Networking", new BigDecimal("9499000.00"), 50, "All-in-one enterprise gateway router with PoE switch and integrated NVR."),
                new ProductSeedSpec("TP-Link Deco BE85 BE22000 Whole Home Mesh WiFi 7 System (2-Pack)", "Networking", new BigDecimal("13999000.00"), 30, "Multi-Link Operation (MLO) with dual 10Gbps ports for multi-gigabit mesh coverage."),
                new ProductSeedSpec("LG UltraGear 27GR95QE-B 27-inch OLED QHD 240Hz Gaming Monitor", "Displays", new BigDecimal("14499000.00"), 55, "0.03ms response time with 98.5% DCI-P3 color gamut and NVIDIA G-SYNC compatible."),
                new ProductSeedSpec("Dell UltraSharp U3224KB 32-inch 6K IPS Black Hub Monitor", "Displays", new BigDecimal("31999000.00"), 25, "Exceptional 6K resolution with built-in 4K HDR webcam and 140W power delivery."),
                new ProductSeedSpec("BenQ PD3220U 32-inch 4K UHD Thunderbolt 3 Designer Monitor", "Displays", new BigDecimal("18999000.00"), 40, "Calman verified and Pantone validated color accuracy with Thunderbolt 3 daisy-chain."),
                new ProductSeedSpec("Samsung ViewFinity S9 27-inch 5K Studio Display", "Displays", new BigDecimal("19499000.00"), 45, "5120 x 2880 5K IPS panel with Smart Calibration and matte anti-glare display."),
                new ProductSeedSpec("ASUS ProArt Display PA32UCG-K 32-inch 4K HDR 1600 Mini-LED", "Displays", new BigDecimal("38999000.00"), 15, "1152-zone local dimming with true 10-bit color, 120Hz refresh rate, and hardware calibration."),
                new ProductSeedSpec("Alienware AW3423DWF 34-inch Quantum Dot OLED Curved Monitor", "Displays", new BigDecimal("17999000.00"), 40, "1800R curved QD-OLED panel with 165Hz refresh rate and infinite contrast ratio.")
        );
    }
}
