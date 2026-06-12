package com.premiumminds.billy.portugal.services.export.saftpt.v1_03_01.schema;
import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;
/**
 * <p>Java class for SAFTPTMovementTaxType.
 *
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <pre>
 * &lt;simpleType name="SAFTPTMovementTaxType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="IVA"/&gt;
 *     &lt;enumeration value="NS"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 */
@XmlType(name = "SAFTPTMovementTaxType")
@XmlEnum
public enum SAFTPTMovementTaxType {

    IVA,
    NS;

    public String value() {
        return name();
    }

    public static SAFTPTMovementTaxType fromValue(String v) {
        return SAFTPTMovementTaxType.valueOf(v);
    }
}
