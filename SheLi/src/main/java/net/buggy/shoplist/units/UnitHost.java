package net.buggy.shoplist.units;

import android.content.Intent;

public interface UnitHost {
    void claimView(int viewId, Unit unit);

    <T extends Unit> T findUnit(String unitTag);

    void startAnotherActivity(Intent intent, int requestCode, Unit listeningUnit);

}
