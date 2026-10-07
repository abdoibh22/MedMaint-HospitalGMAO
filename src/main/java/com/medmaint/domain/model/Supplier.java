package com.medmaint.domain.model;

/** Fournisseur (équipements, pièces, sous-traitance, contrats). */
public class Supplier {

    private long id;
    private String company;
    private String contactPerson;
    private String address;
    private String phone;
    private String email;
    private String website;
    private String category;
    private String products;
    private String contractStatus;
    private String notes;
    private boolean archived;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getProducts() { return products; }
    public void setProducts(String products) { this.products = products; }

    public String getContractStatus() { return contractStatus; }
    public void setContractStatus(String contractStatus) { this.contractStatus = contractStatus; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }

    @Override
    public String toString() { return company; }
}
