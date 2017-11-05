package net.buggy.shoplist.model;


import com.google.common.collect.ImmutableSet;

import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

public class Product extends Entity implements Serializable {

    private String name;
    private UnitOfMeasure defaultUnits;
    private Integer periodCount;
    private PeriodType periodType = PeriodType.WEEKS;
    private Date lastBuyDate;

    private final Set<Category> categories = new LinkedHashSet<>();

    public Product() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<Category> getCategories() {
        return ImmutableSet.copyOf(categories);
    }

    public void setCategories(Collection<Category> categories) {
        this.categories.clear();

        if (categories != null) {
            this.categories.addAll(categories);
        }
    }

    public UnitOfMeasure getDefaultUnits() {
        return defaultUnits;
    }

    public void setDefaultUnits(UnitOfMeasure defaultUnits) {
        this.defaultUnits = defaultUnits;
    }

    public Integer getPeriodCount() {
        return periodCount;
    }

    public void setPeriodCount(Integer periodCount) {
        this.periodCount = periodCount;
    }

    public PeriodType getPeriodType() {
        return periodType;
    }

    public void setPeriodType(PeriodType periodType) {
        this.periodType = periodType;
    }

    public Date getLastBuyDate() {
        return lastBuyDate;
    }

    public void setLastBuyDate(Date lastBuyDate) {
        this.lastBuyDate = lastBuyDate;
    }

    @Override
    public String toString() {
        return name;
    }
}
