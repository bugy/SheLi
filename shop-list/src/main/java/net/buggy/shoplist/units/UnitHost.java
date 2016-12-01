package net.buggy.shoplist.units;

public interface UnitHost {
    void claimView(int viewId, Unit unit);

    <T extends Unit> T findUnit(String unitTag);
}
