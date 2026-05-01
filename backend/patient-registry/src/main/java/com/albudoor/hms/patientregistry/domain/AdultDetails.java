package com.albudoor.hms.patientregistry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AdultDetails {

    @Column(name = "national_id", length = 50)
    private String nationalId;

    @Column(name = "mobile_number", length = 30)
    private String mobileNumber;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "occupation", length = 200)
    private String occupation;

    @Column(name = "emergency_contact_name", length = 200)
    private String emergencyContactName;

    @Column(name = "emergency_contact_mobile", length = 30)
    private String emergencyContactMobile;
}
