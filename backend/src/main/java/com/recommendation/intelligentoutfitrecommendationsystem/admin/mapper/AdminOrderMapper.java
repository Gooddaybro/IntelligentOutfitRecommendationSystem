package com.recommendation.intelligentoutfitrecommendationsystem.admin.mapper;

import com.recommendation.intelligentoutfitrecommendationsystem.admin.model.AdminOrderRow;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.model.AdminOrderState;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MyBatis boundary for admin order fulfillment so status transitions and shipment SQL stay outside services.
 */
@Mapper
public interface AdminOrderMapper {
    /**
     * Loads the aggregated order table projection for the admin console.
     *
     * @return newest order rows with payment and shipment facts already joined
     */
    List<AdminOrderRow> findOrders();

    /**
     * Reloads a single order through the same projection used by list responses after fulfillment writes.
     *
     * @param orderNo public order number from the admin route
     * @return order row, or null when the order does not exist
     */
    AdminOrderRow findOrderByOrderNo(@Param("orderNo") String orderNo);

    /**
     * Reads only the fields needed to decide whether an order may enter the shipping workflow.
     *
     * @param orderNo public order number from the admin route
     * @return state row, or null when the order does not exist
     */
    AdminOrderState findOrderStateByOrderNo(@Param("orderNo") String orderNo);

    /**
     * Conditionally marks a paid order shipped; the affected row count is the concurrency guard.
     *
     * @param orderId database order id resolved before the transition
     * @return one when the order was still PAID, otherwise zero
     */
    int markOrderShipped(@Param("orderId") Long orderId);

    /**
     * Clears the previous shipment row before replacement inside the fulfillment transaction.
     *
     * @param orderNo public order number whose shipment is being replaced
     * @return deleted row count reported by the database
     */
    int deleteShipmentByOrderNo(@Param("orderNo") String orderNo);

    /**
     * Inserts the shipment row that completes the fulfillment transaction after the status guard succeeds.
     *
     * @param orderId database order id linked to the shipment
     * @param orderNo public order number linked to the shipment
     * @param carrier normalized carrier submitted by the admin
     * @param trackingNo normalized tracking number submitted by the admin
     * @return inserted row count reported by the database
     */
    int insertShipment(@Param("orderId") Long orderId,
                       @Param("orderNo") String orderNo,
                       @Param("carrier") String carrier,
                       @Param("trackingNo") String trackingNo);
}
