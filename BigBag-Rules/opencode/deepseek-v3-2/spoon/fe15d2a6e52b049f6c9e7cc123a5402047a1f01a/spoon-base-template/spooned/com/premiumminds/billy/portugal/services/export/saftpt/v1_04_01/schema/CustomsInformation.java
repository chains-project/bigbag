package com.premiumminds.billy.portugal.services.export.saftpt.v1_04_01.schema;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import org.jvnet.jaxb2_commons.lang.JAXBToStringStrategy;
import org.jvnet.jaxb2_commons.lang.ToString2;
import org.jvnet.jaxb2_commons.lang.ToStringStrategy2;
import org.jvnet.jaxb2_commons.locator.ObjectLocator;
/**
 * <p>Java class for CustomsInformation complex type.
 *
 * <p>The following schema fragment specifies the expected content contained within this class.
 *
 * <pre>
 * &lt;complexType name="CustomsInformation"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element ref="{urn:OECD:StandardAuditFile-Tax:PT_1.04_01}ARCNo" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element ref="{urn:OECD:StandardAuditFile-Tax:PT_1.04_01}IECAmount" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "CustomsInformation", propOrder = { "arcNo", "iecAmount" })
public class CustomsInformation implements ToString2 {
    @XmlElement(name = "ARCNo")
    protected List<String> arcNo;

    @XmlElement(name = "IECAmount")
    protected BigDecimal iecAmount;

    /**
     * Gets the value of the arcNo property.
     *
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the arcNo property.
     *
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getARCNo().add(newItem);
     * </pre>
     *
     *
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String}
     */
    public List<String> getARCNo() {
        if (arcNo == null) {
            arcNo = new ArrayList<String>();
        }
        return this.arcNo;
    }

    /**
     * Gets the value of the iecAmount property.
     *
     * @return possible object is
    {@link BigDecimal}
     */
    public BigDecimal getIECAmount() {
        return iecAmount;
    }

    /**
     * Sets the value of the iecAmount property.
     *
     * @param value
     * 		allowed object is
     * 		{@link BigDecimal}
     */
    public void setIECAmount(BigDecimal value) {
        this.iecAmount = value;
    }

    @Override
    public String toString() {
        final ToStringStrategy2 strategy = JAXBToStringStrategy.INSTANCE;
        final StringBuilder buffer = new StringBuilder();
        append(null, buffer, strategy);
        return buffer.toString();
    }

    @Override
    public StringBuilder append(ObjectLocator locator, StringBuilder buffer, ToStringStrategy2 strategy) {
        strategy.appendStart(locator, this, buffer);
        appendFields(locator, buffer, strategy);
        strategy.appendEnd(locator, this, buffer);
        return buffer;
    }

    @Override
    public StringBuilder appendFields(ObjectLocator locator, StringBuilder buffer, ToStringStrategy2 strategy) {
        {
            List<String> theARCNo;
            theARCNo = ((this.arcNo != null) && (!this.arcNo.isEmpty())) ? this.getARCNo() : null;
            strategy.appendField(locator, this, "arcNo", buffer, theARCNo, (this.arcNo != null) && (!this.arcNo.isEmpty()));
        }
        {
            BigDecimal theIECAmount;
            theIECAmount = this.getIECAmount();
            strategy.appendField(locator, this, "iecAmount", buffer, theIECAmount, this.iecAmount != null);
        }
        return buffer;
    }
}
