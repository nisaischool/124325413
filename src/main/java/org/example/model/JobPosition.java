package org.example.model;

public class JobPosition {
    private final int id;
    private final String name;

    public JobPosition(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() { return id; }
    public String getName() { return name; }
}