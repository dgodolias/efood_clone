package com.example.backend;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Map;

public class BackendTest {
    private Backend backend;

    @Before
    public void setUp() {
        backend = new Backend();
    }

    @Test
    public void testAddStore() {
        Store store = new Store("TestStore", 0, 0, "pizzeria", 3, 10, "");
        backend.addStore(store);
        assertEquals(1, backend.getStores().size());
        assertEquals("TestStore", backend.getStores().get(0).getStoreName());
    }

    @Test
    public void testRemoveStore() {
        Store store = new Store("TestStore", 0, 0, "pizzeria", 3, 10, "");
        backend.addStore(store);
        backend.removeStore("TestStore");
        assertEquals(0, backend.getStores().size());
    }

    @Test
    public void testAddProductToStore() {
        Store store = new Store("TestStore", 0, 0, "pizzeria", 3, 10, "");
        backend.addStore(store);
        Product product = new Product("margarita", "pizza", 5000, 9.2);
        backend.addProductToStore("TestStore", product);
        assertEquals(1, backend.getStores().get(0).getProducts().size());
        assertEquals("margarita", backend.getStores().get(0).getProducts().get(0).getProductName());
    }

    @Test
    public void testGetFoodCategoryStats() {
        Store store1 = new Store("PizzaFun", 0, 0, "pizzeria", 3, 10, "");
        Store store2 = new Store("BurgerKing", 0, 0, "burger", 4, 15, "");
        backend.addStore(store1);
        backend.addStore(store2);
        backend.addProductToStore("PizzaFun", new Product("margarita", "pizza", 5000, 9.2));
        Map<String, Integer> stats = backend.getFoodCategoryStats("pizzeria");
        assertEquals(1, stats.get("PizzaFun").intValue());
        assertEquals(1, stats.get("total").intValue());
        assertNull(stats.get("BurgerKing"));
    }
}