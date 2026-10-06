package com.azuriom.azlink.common.data;

public class UserInfo {

    private final int id;
    private final String name;
    /** Donate / site money (read-only from game servers on current API). */
    private double money;
    /** Game currency (coins) — editable via /coins endpoints. */
    private double coins;

    public UserInfo(int id, String name, double money) {
        this(id, name, money, 0);
    }

    public UserInfo(int id, String name, double money, double coins) {
        this.id = id;
        this.name = name;
        this.money = money;
        this.coins = coins;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setMoney(double money) {
        this.money = money;
    }

    public double getMoney() {
        return money;
    }

    public void setCoins(double coins) {
        this.coins = coins;
    }

    public double getCoins() {
        return coins;
    }
}
