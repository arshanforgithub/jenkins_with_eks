
package com.example;

public class App {

    public static void main(String[] args) {
        System.out.println(getGreeting());
    }

    public static String getGreeting() {
        return "Hello from Jenkins on EKS with Karpenter!";
    }

    public static int add(int a, int b) {
        return a + b;
    }
}
