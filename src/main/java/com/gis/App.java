package com.gis;
import	java.util.stream.Collectors;
import java.util.Arrays;
import	java.util.List;

public class App {
    public static void main(String[] args) {
        List<Object> list = Arrays.asList(1, 2, 5, 6, 7, 8).stream()
                .filter(arg -> arg % 2 != 0) // 1,5,7
                .map(arg -> arg / 2 + 1) // 1 3 4
                .map(arg -> arg * 2) // 2 6 8
                .collect(Collectors.toList());
        System.out.println(list);
    }
}
