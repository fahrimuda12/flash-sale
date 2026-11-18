package com.fahri.order.repository;

import com.fahri.order.document.OrderSearchDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderSearchRepository extends ElasticsearchRepository<OrderSearchDocument, String> {

    List<OrderSearchDocument> findByOrderNumber(String orderNumber);
}
