package com.pistonpunk.gigahack26.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VendorRegistryTest {

    @Test
    void anAutomationPlatformIsAVendor() {
        assertTrue(VendorRegistry.isVendor("UiPath", "Automation Software"));
        assertTrue(VendorRegistry.isVendor("Nintex", "Software"));
        assertTrue(VendorRegistry.isVendor("Celonis", "Process Mining"));
        assertTrue(VendorRegistry.isVendor("Appian", "Software"));
        assertTrue(VendorRegistry.isVendor("Automation Anywhere", "Automation Software"));
    }

    @Test
    void aLegalSuffixDoesNotHideAVendor() {
        assertTrue(VendorRegistry.namedVendor("UiPath, Inc."));
        assertTrue(VendorRegistry.namedVendor("Celonis SE"));
        assertTrue(VendorRegistry.namedVendor("Nintex UK Ltd"));
        assertTrue(VendorRegistry.namedVendor("SS&C Technologies Holdings"));
    }

    @Test
    void anAmpersandIsNotPartOfTheName() {
        assertTrue(VendorRegistry.namedVendor("SS&C"));
        assertTrue(VendorRegistry.namedVendor("SS&C Technologies"));
    }

    @Test
    void aVendorIndustryIsEnoughWithoutAName() {
        assertTrue(VendorRegistry.vendorIndustry("Process Mining"));
        assertTrue(VendorRegistry.isVendor("Some New RPA Startup", "Robotic Process Automation"));
    }

    @Test
    void anOrdinarySoftwareCompanyIsABuyer() {
        assertFalse(VendorRegistry.isVendor("Dassault Systemes", "Software"));
        assertFalse(VendorRegistry.isVendor("Nemetschek", "Software"));
        assertFalse(VendorRegistry.isVendor("TeamViewer", "Software"));
        assertFalse(VendorRegistry.isVendor("Sage", "Enterprise Software"));
        assertFalse(VendorRegistry.isVendor("Visma", "Enterprise Software"));
    }

    @Test
    void anIndustrialAutomationVendorIsNotTheCompetitor() {
        assertFalse(VendorRegistry.isVendor("Siemens", "Industrial Automation"));
        assertFalse(VendorRegistry.isVendor("ABB", "Industrial Automation"));
        assertFalse(VendorRegistry.isVendor("Rockwell Automation", "Industrial Automation"));
    }

    @Test
    void aCompanyThatBuysIsNotAVendor() {
        assertFalse(VendorRegistry.isVendor("Lufthansa Group", "Airlines"));
        assertFalse(VendorRegistry.isVendor("DHL Group", "Logistics"));
        assertFalse(VendorRegistry.isVendor("Reckitt", "Consumer Goods"));
    }

    @Test
    void aNullNameIsNotAVendor() {
        assertFalse(VendorRegistry.isVendor(null, "Software"));
        assertFalse(VendorRegistry.namedVendor(null));
        assertFalse(VendorRegistry.vendorIndustry(null));
    }
}
