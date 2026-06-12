package com.premiumminds.billy.portugal.services.export.saftpt.v1_03_01.schema;
import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;
/**
 * <p>Java class for SAFTPTPaymentType.
 *
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <pre>
 * &lt;simpleType name="SAFTPTPaymentType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="RC"/&gt;
 *     &lt;enumeration value="RG"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 */
@XmlType(name = "SAFTPTPaymentType")
@XmlEnum
public enum SAFTPTPaymentType {

    RC,
    RG;

    public String value() {
        return name();
    }

    public static SAFTPTPaymentType fromValue(String v) {
        return SAFTPTPaymentType.valueOf(v);
    }
}
