package com.example;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AppTest {

    @Test
    void testGreeting() {
        assertEquals("Hello from Jenkins on EKS with Karpenter!", App.getGreeting());
    }

    @Test
    void testAdd() {
        assertEquals(5, App.add(2, 3));
    }

    @Test
    void testAddNegative() {
        assertEquals(-1, App.add(2, -3));
    }
}
