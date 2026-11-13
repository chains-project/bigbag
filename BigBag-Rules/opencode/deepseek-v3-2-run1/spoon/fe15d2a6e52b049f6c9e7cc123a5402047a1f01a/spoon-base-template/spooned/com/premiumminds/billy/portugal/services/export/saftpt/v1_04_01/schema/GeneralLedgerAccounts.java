package com.premiumminds.billy.portugal.services.export.saftpt.v1_04_01.schema;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import org.jvnet.jaxb2_commons.lang.JAXBToStringStrategy;
import org.jvnet.jaxb2_commons.lang.ToString2;
import org.jvnet.jaxb2_commons.lang.ToStringStrategy2;
import org.jvnet.jaxb2_commons.locator.ObjectLocator;
/**
 * <p>Java class for anonymous complex type.
 *
 * <p>The following schema fragment specifies the expected content contained within this class.
 *
 * <pre>
 * &lt;complexType&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element ref="{urn:OECD:StandardAuditFile-Tax:PT_1.04_01}TaxonomyReference"/&gt;
 *         &lt;element name="Account" maxOccurs="unbounded"&gt;
 *           &lt;complexType&gt;
 *             &lt;complexContent&gt;
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *                 &lt;sequence&gt;
 *                   &lt;element name="AccountID" type="{urn:OECD:StandardAuditFile-Tax:PT_1.04_01}SAFPTGLAccountID"/&gt;
 *                   &lt;element ref="{urn:OECD:StandardAuditFile-Tax:PT_1.04_01}AccountDescription"/&gt;
 *                   &lt;element ref="{urn:OECD:StandardAuditFile-Tax:PT_1.04_01}OpeningDebitBalance"/&gt;
 *                   &lt;element ref="{urn:OECD:StandardAuditFile-Tax:PT_1.04_01}OpeningCreditBalance"/&gt;
 *                   &lt;element ref="{urn:OECD:StandardAuditFile-Tax:PT_1.04_01}ClosingDebitBalance"/&gt;
 *                   &lt;element ref="{urn:OECD:StandardAuditFile-Tax:PT_1.04_01}ClosingCreditBalance"/&gt;
 *                   &lt;element ref="{urn:OECD:StandardAuditFile-Tax:PT_1.04_01}GroupingCategory"/&gt;
 *                   &lt;element name="GroupingCode" type="{urn:OECD:StandardAuditFile-Tax:PT_1.04_01}SAFPTGLAccountID" minOccurs="0"/&gt;
 *                   &lt;element name="TaxonomyCode" type="{urn:OECD:StandardAuditFile-Tax:PT_1.04_01}SAFTaxonomyCode" minOccurs="0"/&gt;
 *                 &lt;/sequence&gt;
 *               &lt;/restriction&gt;
 *             &lt;/complexContent&gt;
 *           &lt;/complexType&gt;
 *         &lt;/element&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = { "taxonomyReference", "account" })
@XmlRootElement(name = "GeneralLedgerAccounts")
public class GeneralLedgerAccounts implements ToString2 {
    @XmlElement(name = "TaxonomyReference", required = true)
    protected String taxonomyReference;

    @XmlElement(name = "Account", required = true)
    protected List<GeneralLedgerAccounts.Account> account;

    /**
     * Gets the value of the taxonomyReference property.
     *
     * @return possible object is
    {@link String}
     */
    public String getTaxonomyReference() {
        return taxonomyReference;
    }

    /**
     * Sets the value of the taxonomyReference property.
     *
     * @param value
     * 		allowed object is
     * 		{@link String}
     */
    public void setTaxonomyReference(String value) {
        this.taxonomyReference = value;
    }

    /**
     * Gets the value of the account property.
     *
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the account property.
     *
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getAccount().add(newItem);
     * </pre>
     *
     *
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link GeneralLedgerAccounts.Account}
     */
    public List<GeneralLedgerAccounts.Account> getAccount() {
        if (account == null) {
            account = new ArrayList<GeneralLedgerAccounts.Account>();
        }
        return this.account;
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
            String theTaxonomyReference;
            theTaxonomyReference = this.getTaxonomyReference();
            strategy.appendField(locator, this, "taxonomyReference", buffer, theTaxonomyReference, this.taxonomyReference != null);
        }
        {
            List<GeneralLedgerAccounts.Account> theAccount;
            theAccount = ((this.account != null) && (!this.account.isEmpty())) ? this.getAccount() : null;
            strategy.appendField(locator, this, "account", buffer, theAccount, (this.account != null) && (!this.account.isEmpty()));
        }
        return buffer;
    }

    /**
     * <p>Java class for anonymous complex type.
     *
     * <p>The following schema fragment specifies the expected content contained within this class.
     *
     * <pre>
     * &lt;complexType&gt;
     *   &lt;complexContent&gt;
     *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
     *       &lt;sequence&gt;
     *         &lt;element name="AccountID" type="{urn:OECD:StandardAuditFile-Tax:PT_1.04_01}SAFPTGLAccountID"/&gt;
     *         &lt;element ref="{urn:OECD:StandardAuditFile-Tax:PT_1.04_01}AccountDescription"/&gt;
     *         &lt;element ref="{urn:OECD:StandardAuditFile-Tax:PT_1.04_01}OpeningDebitBalance"/&gt;
     *         &lt;element ref="{urn:OECD:StandardAuditFile-Tax:PT_1.04_01}OpeningCreditBalance"/&gt;
     *         &lt;element ref="{urn:OECD:StandardAuditFile-Tax:PT_1.04_01}ClosingDebitBalance"/&gt;
     *         &lt;element ref="{urn:OECD:StandardAuditFile-Tax:PT_1.04_01}ClosingCreditBalance"/&gt;
     *         &lt;element ref="{urn:OECD:StandardAuditFile-Tax:PT_1.04_01}GroupingCategory"/&gt;
     *         &lt;element name="GroupingCode" type="{urn:OECD:StandardAuditFile-Tax:PT_1.04_01}SAFPTGLAccountID" minOccurs="0"/&gt;
     *         &lt;element name="TaxonomyCode" type="{urn:OECD:StandardAuditFile-Tax:PT_1.04_01}SAFTaxonomyCode" minOccurs="0"/&gt;
     *       &lt;/sequence&gt;
     *     &lt;/restriction&gt;
     *   &lt;/complexContent&gt;
     * &lt;/complexType&gt;
     * </pre>
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "", propOrder = { "accountID", "accountDescription", "openingDebitBalance", "openingCreditBalance", "closingDebitBalance", "closingCreditBalance", "groupingCategory", "groupingCode", "taxonomyCode" })
    public static class Account implements ToString2 {
        @XmlElement(name = "AccountID", required = true)
        protected String accountID;

        @XmlElement(name = "AccountDescription", required = true)
        protected String accountDescription;

        @XmlElement(name = "OpeningDebitBalance", required = true)
        protected BigDecimal openingDebitBalance;

        @XmlElement(name = "OpeningCreditBalance", required = true)
        protected BigDecimal openingCreditBalance;

        @XmlElement(name = "ClosingDebitBalance", required = true)
        protected BigDecimal closingDebitBalance;

        @XmlElement(name = "ClosingCreditBalance", required = true)
        protected BigDecimal closingCreditBalance;

        @XmlElement(name = "GroupingCategory", required = true)
        protected String groupingCategory;

        @XmlElement(name = "GroupingCode")
        protected String groupingCode;

        @XmlElement(name = "TaxonomyCode")
        @XmlSchemaType(name = "integer")
        protected Integer taxonomyCode;

        /**
         * Gets the value of the accountID property.
         *
         * @return possible object is
        {@link String}
         */
        public String getAccountID() {
            return accountID;
        }

        /**
         * Sets the value of the accountID property.
         *
         * @param value
         * 		allowed object is
         * 		{@link String}
         */
        public void setAccountID(String value) {
            this.accountID = value;
        }

        /**
         * Gets the value of the accountDescription property.
         *
         * @return possible object is
        {@link String}
         */
        public String getAccountDescription() {
            return accountDescription;
        }

        /**
         * Sets the value of the accountDescription property.
         *
         * @param value
         * 		allowed object is
         * 		{@link String}
         */
        public void setAccountDescription(String value) {
            this.accountDescription = value;
        }

        /**
         * Gets the value of the openingDebitBalance property.
         *
         * @return possible object is
        {@link BigDecimal}
         */
        public BigDecimal getOpeningDebitBalance() {
            return openingDebitBalance;
        }

        /**
         * Sets the value of the openingDebitBalance property.
         *
         * @param value
         * 		allowed object is
         * 		{@link BigDecimal}
         */
        public void setOpeningDebitBalance(BigDecimal value) {
            this.openingDebitBalance = value;
        }

        /**
         * Gets the value of the openingCreditBalance property.
         *
         * @return possible object is
        {@link BigDecimal}
         */
        public BigDecimal getOpeningCreditBalance() {
            return openingCreditBalance;
        }

        /**
         * Sets the value of the openingCreditBalance property.
         *
         * @param value
         * 		allowed object is
         * 		{@link BigDecimal}
         */
        public void setOpeningCreditBalance(BigDecimal value) {
            this.openingCreditBalance = value;
        }

        /**
         * Gets the value of the closingDebitBalance property.
         *
         * @return possible object is
        {@link BigDecimal}
         */
        public BigDecimal getClosingDebitBalance() {
            return closingDebitBalance;
        }

        /**
         * Sets the value of the closingDebitBalance property.
         *
         * @param value
         * 		allowed object is
         * 		{@link BigDecimal}
         */
        public void setClosingDebitBalance(BigDecimal value) {
            this.closingDebitBalance = value;
        }

        /**
         * Gets the value of the closingCreditBalance property.
         *
         * @return possible object is
        {@link BigDecimal}
         */
        public BigDecimal getClosingCreditBalance() {
            return closingCreditBalance;
        }

        /**
         * Sets the value of the closingCreditBalance property.
         *
         * @param value
         * 		allowed object is
         * 		{@link BigDecimal}
         */
        public void setClosingCreditBalance(BigDecimal value) {
            this.closingCreditBalance = value;
        }

        /**
         * Gets the value of the groupingCategory property.
         *
         * @return possible object is
        {@link String}
         */
        public String getGroupingCategory() {
            return groupingCategory;
        }

        /**
         * Sets the value of the groupingCategory property.
         *
         * @param value
         * 		allowed object is
         * 		{@link String}
         */
        public void setGroupingCategory(String value) {
            this.groupingCategory = value;
        }

        /**
         * Gets the value of the groupingCode property.
         *
         * @return possible object is
        {@link String}
         */
        public String getGroupingCode() {
            return groupingCode;
        }

        /**
         * Sets the value of the groupingCode property.
         *
         * @param value
         * 		allowed object is
         * 		{@link String}
         */
        public void setGroupingCode(String value) {
            this.groupingCode = value;
        }

        /**
         * Gets the value of the taxonomyCode property.
         *
         * @return possible object is
        {@link Integer}
         */
        public Integer getTaxonomyCode() {
            return taxonomyCode;
        }

        /**
         * Sets the value of the taxonomyCode property.
         *
         * @param value
         * 		allowed object is
         * 		{@link Integer}
         */
        public void setTaxonomyCode(Integer value) {
            this.taxonomyCode = value;
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
                String theAccountID;
                theAccountID = this.getAccountID();
                strategy.appendField(locator, this, "accountID", buffer, theAccountID, this.accountID != null);
            }
            {
                String theAccountDescription;
                theAccountDescription = this.getAccountDescription();
                strategy.appendField(locator, this, "accountDescription", buffer, theAccountDescription, this.accountDescription != null);
            }
            {
                BigDecimal theOpeningDebitBalance;
                theOpeningDebitBalance = this.getOpeningDebitBalance();
                strategy.appendField(locator, this, "openingDebitBalance", buffer, theOpeningDebitBalance, this.openingDebitBalance != null);
            }
            {
                BigDecimal theOpeningCreditBalance;
                theOpeningCreditBalance = this.getOpeningCreditBalance();
                strategy.appendField(locator, this, "openingCreditBalance", buffer, theOpeningCreditBalance, this.openingCreditBalance != null);
            }
            {
                BigDecimal theClosingDebitBalance;
                theClosingDebitBalance = this.getClosingDebitBalance();
                strategy.appendField(locator, this, "closingDebitBalance", buffer, theClosingDebitBalance, this.closingDebitBalance != null);
            }
            {
                BigDecimal theClosingCreditBalance;
                theClosingCreditBalance = this.getClosingCreditBalance();
                strategy.appendField(locator, this, "closingCreditBalance", buffer, theClosingCreditBalance, this.closingCreditBalance != null);
            }
            {
                String theGroupingCategory;
                theGroupingCategory = this.getGroupingCategory();
                strategy.appendField(locator, this, "groupingCategory", buffer, theGroupingCategory, this.groupingCategory != null);
            }
            {
                String theGroupingCode;
                theGroupingCode = this.getGroupingCode();
                strategy.appendField(locator, this, "groupingCode", buffer, theGroupingCode, this.groupingCode != null);
            }
            {
                Integer theTaxonomyCode;
                theTaxonomyCode = this.getTaxonomyCode();
                strategy.appendField(locator, this, "taxonomyCode", buffer, theTaxonomyCode, this.taxonomyCode != null);
            }
            return buffer;
        }
    }
}
