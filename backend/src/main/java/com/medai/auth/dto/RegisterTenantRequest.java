package com.medai.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterTenantRequest {

    @NotBlank(message = "Hospital name is required")
    @Size(min = 2, max = 255)
    private String hospitalName;

    @NotBlank(message = "Subdomain is required")
    @Size(min = 3, max = 50)
    private String subdomain;

    @NotBlank(message = "Contact email is required")
    @Email
    private String contactEmail;

    private String phone;
    private String address;

    @NotBlank(message = "Admin first name is required")
    private String adminFirstName;

    @NotBlank(message = "Admin last name is required")
    private String adminLastName;

    @NotBlank(message = "Admin email is required")
    @Email
    private String adminEmail;

    @NotBlank(message = "Admin password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String adminPassword;
}
