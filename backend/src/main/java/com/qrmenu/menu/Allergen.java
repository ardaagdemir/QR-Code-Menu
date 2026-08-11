package com.qrmenu.menu;

/**
 * Structured allergen list (Section 3.2: "yalnızca serbest metin olmamalıdır") - the
 * 14 EU-regulated food allergens (EU 1169/2011 Annex II), the same reference set most
 * restaurant menu tooling uses rather than inventing a bespoke taxonomy.
 */
public enum Allergen {
    GLUTEN,
    CRUSTACEANS,
    EGGS,
    FISH,
    PEANUTS,
    SOYBEANS,
    MILK,
    TREE_NUTS,
    CELERY,
    MUSTARD,
    SESAME,
    SULPHITES,
    LUPIN,
    MOLLUSCS
}
