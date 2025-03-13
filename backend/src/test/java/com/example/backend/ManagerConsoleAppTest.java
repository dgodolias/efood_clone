package com.example.backend;

import org.junit.Test;
import java.io.*;

import static org.junit.Assert.*;

public class ManagerConsoleAppTest {
    @Test
    public void testAddStore() {
        String input = "1\nTestStore\n0\n0\npizzeria\n3\n10\nlogo.png\n4\n";
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes());
        System.setIn(in);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        Backend backend = new Backend();
        ManagerConsoleApp app = new ManagerConsoleApp(backend);
        app.start();

        String output = out.toString();
        assertTrue(output.contains("Stores:"));
        assertTrue(output.contains("TestStore"));
    }
}