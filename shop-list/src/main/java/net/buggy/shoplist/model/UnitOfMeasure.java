package net.buggy.shoplist.model;


import net.buggy.shoplist.R;

public enum UnitOfMeasure {
    KILOGRAM(R.string.unit_of_measure_kg, R.string.unit_of_measure_kg_short),
    GRAM(R.string.unit_of_measure_gram, R.string.unit_of_measure_gram_short),
    LITER(R.string.unit_of_measure_liter, R.string.unit_of_measure_liter_short),
    BOTTLE(R.string.unit_of_measure_bottle, R.string.unit_of_measure_bottle_short),
    PACK(R.string.unit_of_measure_pack, R.string.unit_of_measure_pack_short),
    PIECE(R.string.unit_of_measure_piece, R.string.unit_of_measure_piece_short);

    private final int fullNameKey;
    private final int shortNameKey;

    UnitOfMeasure(int fullNameKey, int shortNameKey) {
        this.fullNameKey = fullNameKey;
        this.shortNameKey = shortNameKey;
    }

    public int getFullNameKey() {
        return fullNameKey;
    }

    public int getShortNameKey() {
        return shortNameKey;
    }
}
