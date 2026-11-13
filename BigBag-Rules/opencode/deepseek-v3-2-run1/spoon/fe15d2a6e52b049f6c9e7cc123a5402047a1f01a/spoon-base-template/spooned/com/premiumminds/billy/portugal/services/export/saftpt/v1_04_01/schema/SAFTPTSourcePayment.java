package com.premiumminds.billy.portugal.services.export.saftpt.v1_04_01.schema;
import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;
/**
 * <p>Java class for SAFTPTSourcePayment.
 *
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <pre>
 * &lt;simpleType name="SAFTPTSourcePayment"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="P"/&gt;
 *     &lt;enumeration value="I"/&gt;
 *     &lt;enumeration value="M"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 */
@XmlType(name = "SAFTPTSourcePayment")
@XmlEnum
public enum SAFTPTSourcePayment {

    P,
    I,
    M;

    public String value() {
        return name();
    }

    public static SAFTPTSourcePayment fromValue(String v) {
        return SAFTPTSourcePayment.valueOf(v);
    }
}
