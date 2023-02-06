package io.camunda.zeebe.spring.example;


import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Order {

    private String orderId;
    private String articleId;
    private int articleQuantity;
    private int articlePrice;
    private int orderAmount;
    private Boolean paymentRetrieved;


    private Boolean goodsFetched;
    private Boolean goodsShipped;
    private Boolean paymentShouldFail;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getArticleId() {
        return articleId;
    }

    public void setArticleId(String articleId) {
        this.articleId = articleId;
    }

    public int getArticleQuantity() {
        return articleQuantity;
    }

    public void setArticleQuantity(int articleQuantity) {
        this.articleQuantity = articleQuantity;
    }

    public int getArticlePrice() {
        return articlePrice;
    }

    public void setArticlePrice(int articlePrice) {
        this.articlePrice = articlePrice;
    }

    public int getOrderAmount() {
        return orderAmount;
    }

    public void setOrderAmount(int orderAmount) {
        this.orderAmount = orderAmount;
    }

    public Boolean getGoodsFetched() {
        return goodsFetched;
    }

    public void setGoodsFetched(Boolean goodsFetched) {
        this.goodsFetched = goodsFetched;
    }

    public Boolean getGoodsShipped() {
        return goodsShipped;
    }

    public void setGoodsShipped(Boolean goodsShipped) {
        this.goodsShipped = goodsShipped;
    }

    public Boolean getPaymentShouldFail() {
        return paymentShouldFail;
    }

    public void setPaymentShouldFail(Boolean paymentShouldFail) {
        this.paymentShouldFail = paymentShouldFail;
    }

    public Boolean getPaymentRetrieved() {
        return paymentRetrieved;
    }

    public void setPaymentRetrieved(Boolean paymentRetrieved) {
        this.paymentRetrieved = paymentRetrieved;
    }

    @Override
    public String toString() {
        return "Order{" +
                "orderId='" + orderId + '\'' +
                ", articleId='" + articleId + '\'' +
                ", articleQuantity=" + articleQuantity +
                ", articlePrice=" + articlePrice +
                '}';
    }
}
