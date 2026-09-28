package br.com.agendafacilpro.provisioning;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.provisioning")
public class ProvisioningProperties {
    private boolean enabled;
    private String command;
    private String confirmSlug;
    private String name;
    private String slug;
    private String whatsapp;
    private String city;
    private String description;
    private String ownerName;
    private String ownerEmail;
    private String passwordOutputFile;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
    public String getConfirmSlug() { return confirmSlug; }
    public void setConfirmSlug(String confirmSlug) { this.confirmSlug = confirmSlug; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getWhatsapp() { return whatsapp; }
    public void setWhatsapp(String whatsapp) { this.whatsapp = whatsapp; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getOwnerEmail() { return ownerEmail; }
    public void setOwnerEmail(String ownerEmail) { this.ownerEmail = ownerEmail; }
    public String getPasswordOutputFile() { return passwordOutputFile; }
    public void setPasswordOutputFile(String passwordOutputFile) { this.passwordOutputFile = passwordOutputFile; }
}
