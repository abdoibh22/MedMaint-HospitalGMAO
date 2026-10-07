package com.medmaint.domain.model;

/** Fabricant / marque d'équipement. */
public class Manufacturer {

    private long id;
    private String name;
    private String country;
    private String website;
    private String supportEmail;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }

    public String getSupportEmail() { return supportEmail; }
    public void setSupportEmail(String supportEmail) { this.supportEmail = supportEmail; }

    @Override
    public String toString() { return name; }
}
