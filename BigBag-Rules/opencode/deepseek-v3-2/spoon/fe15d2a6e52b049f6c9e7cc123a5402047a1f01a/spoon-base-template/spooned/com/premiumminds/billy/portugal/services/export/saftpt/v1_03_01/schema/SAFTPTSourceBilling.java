package com.premiumminds.billy.portugal.services.export.saftpt.v1_03_01.schema;
import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;
/**
 * <p>Java class for SAFTPTSourceBilling.
 *
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <pre>
 * &lt;simpleType name="SAFTPTSourceBilling"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="P"/&gt;
 *     &lt;enumeration value="I"/&gt;
 *     &lt;enumeration value="M"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 */
@XmlType(name = "SAFTPTSourceBilling")
@XmlEnum
public enum SAFTPTSourceBilling {

    P,
    I,
    M;

    public String value() {
        return name();
    }

    public static SAFTPTSourceBilling fromValue(String v) {
        return SAFTPTSourceBilling.valueOf(v);
    }
}
