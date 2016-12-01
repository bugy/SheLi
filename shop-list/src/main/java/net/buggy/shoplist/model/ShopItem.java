package net.buggy.shoplist.model;


import java.io.Serializable;
import java.math.BigDecimal;

public class ShopItem extends Entity implements Serializable {

    private Product product;
    private BigDecimal quantity;

    public void setProduct(Product product) {
        this.product = product;
    }

    public Product getProduct() {
        return product;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    @Override
    public String toString() {
        return product.getName();
    }

}
