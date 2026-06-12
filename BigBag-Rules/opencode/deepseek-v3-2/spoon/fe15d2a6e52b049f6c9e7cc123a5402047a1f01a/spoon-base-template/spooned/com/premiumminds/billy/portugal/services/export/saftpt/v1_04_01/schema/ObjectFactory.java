package com.premiumminds.billy.portugal.services.export.saftpt.v1_04_01.schema;
import java.math.BigDecimal;
import java.math.BigInteger;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
/**
 * This object contains factory methods for each
 * Java content interface and Java element interface
 * generated in the com.premiumminds.billy.portugal.services.export.saftpt.v1_04_01.schema package.
 * <p>An ObjectFactory allows you to programatically
 * construct new instances of the Java representation
 * for XML content. The Java representation of XML
 * content can consist of schema derived interfaces
 * and classes representing the binding of schema
 * type definitions, element declarations and model
 * groups.  Factory methods for each of these are
 * provided in this class.
 */
@XmlRegistry
public class ObjectFactory {
    private static final QName _AuditFileVersion_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "AuditFileVersion");

    private static final QName _CompanyID_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "CompanyID");

    private static final QName _TaxAccountingBasis_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "TaxAccountingBasis");

    private static final QName _CompanyName_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "CompanyName");

    private static final QName _BusinessName_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "BusinessName");

    private static final QName _CompanyAddress_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "CompanyAddress");

    private static final QName _FiscalYear_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "FiscalYear");

    private static final QName _StartDate_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "StartDate");

    private static final QName _EndDate_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "EndDate");

    private static final QName _DateCreated_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "DateCreated");

    private static final QName _TaxEntity_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "TaxEntity");

    private static final QName _ProductCompanyTaxID_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "ProductCompanyTaxID");

    private static final QName _SoftwareCertificateNumber_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "SoftwareCertificateNumber");

    private static final QName _ProductID_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "ProductID");

    private static final QName _ProductVersion_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "ProductVersion");

    private static final QName _HeaderComment_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "HeaderComment");

    private static final QName _Telephone_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "Telephone");

    private static final QName _Fax_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "Fax");

    private static final QName _Email_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "Email");

    private static final QName _Website_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "Website");

    private static final QName _NumberOfEntries_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "NumberOfEntries");

    private static final QName _TotalDebit_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "TotalDebit");

    private static final QName _TotalCredit_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "TotalCredit");

    private static final QName _TaxonomyReference_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "TaxonomyReference");

    private static final QName _CustomerID_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "CustomerID");

    private static final QName _AccountID_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "AccountID");

    private static final QName _CustomerTaxID_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "CustomerTaxID");

    private static final QName _Contact_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "Contact");

    private static final QName _BillingAddress_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "BillingAddress");

    private static final QName _ShipToAddress_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "ShipToAddress");

    private static final QName _SelfBillingIndicator_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "SelfBillingIndicator");

    private static final QName _SupplierID_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "SupplierID");

    private static final QName _SupplierTaxID_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "SupplierTaxID");

    private static final QName _ProductType_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "ProductType");

    private static final QName _ProductCode_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "ProductCode");

    private static final QName _ProductGroup_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "ProductGroup");

    private static final QName _ProductDescription_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "ProductDescription");

    private static final QName _ProductNumberCode_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "ProductNumberCode");

    private static final QName _TaxType_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "TaxType");

    private static final QName _TaxCountryRegion_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "TaxCountryRegion");

    private static final QName _TaxExpirationDate_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "TaxExpirationDate");

    private static final QName _TaxPercentage_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "TaxPercentage");

    private static final QName _TaxAmount_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "TaxAmount");

    private static final QName _AccountDescription_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "AccountDescription");

    private static final QName _Address_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "Address");

    private static final QName _AddressDetail_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "AddressDetail");

    private static final QName _ARCNo_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "ARCNo");

    private static final QName _ATCUD_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "ATCUD");

    private static final QName _ATDocCodeID_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "ATDocCodeID");

    private static final QName _BuildingNumber_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "BuildingNumber");

    private static final QName _City_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "City");

    private static final QName _ClosingCreditBalance_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "ClosingCreditBalance");

    private static final QName _ClosingDebitBalance_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "ClosingDebitBalance");

    private static final QName _CNCode_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "CNCode");

    private static final QName _CreditAmount_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "CreditAmount");

    private static final QName _CurrencyAmount_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "CurrencyAmount");

    private static final QName _DebitAmount_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "DebitAmount");

    private static final QName _DeliveryDate_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "DeliveryDate");

    private static final QName _DeliveryID_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "DeliveryID");

    private static final QName _Description_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "Description");

    private static final QName _DocArchivalNumber_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "DocArchivalNumber");

    private static final QName _ExchangeRate_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "ExchangeRate");

    private static final QName _GLPostingDate_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "GLPostingDate");

    private static final QName _GrossTotal_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "GrossTotal");

    private static final QName _Hash_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "Hash");

    private static final QName _HashControl_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "HashControl");

    private static final QName _IECAmount_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "IECAmount");

    private static final QName _InvoiceDate_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "InvoiceDate");

    private static final QName _InvoiceStatusDate_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "InvoiceStatusDate");

    private static final QName _JournalID_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "JournalID");

    private static final QName _LineNumber_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "LineNumber");

    private static final QName _LocationID_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "LocationID");

    private static final QName _MovementComments_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "MovementComments");

    private static final QName _MovementDate_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "MovementDate");

    private static final QName _MovementEndTime_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "MovementEndTime");

    private static final QName _MovementStartTime_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "MovementStartTime");

    private static final QName _MovementStatusDate_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "MovementStatusDate");

    private static final QName _NetTotal_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "NetTotal");

    private static final QName _NumberOfMovementLines_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "NumberOfMovementLines");

    private static final QName _OpeningCreditBalance_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "OpeningCreditBalance");

    private static final QName _OpeningDebitBalance_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "OpeningDebitBalance");

    private static final QName _OrderDate_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "OrderDate");

    private static final QName _OriginatingON_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "OriginatingON");

    private static final QName _PaymentStatusDate_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "PaymentStatusDate");

    private static final QName _PostalCode_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "PostalCode");

    private static final QName _Quantity_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "Quantity");

    private static final QName _Reason_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "Reason");

    private static final QName _RecordID_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "RecordID");

    private static final QName _Reference_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "Reference");

    private static final QName _Region_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "Region");

    private static final QName _SerialNumber_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "SerialNumber");

    private static final QName _SettlementAmount_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "SettlementAmount");

    private static final QName _ShipFrom_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "ShipFrom");

    private static final QName _ShipFromAddress_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "ShipFromAddress");

    private static final QName _ShipTo_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "ShipTo");

    private static final QName _SourceDocumentID_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "SourceDocumentID");

    private static final QName _SourceID_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "SourceID");

    private static final QName _StreetName_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "StreetName");

    private static final QName _SystemEntryDate_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "SystemEntryDate");

    private static final QName _SystemID_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "SystemID");

    private static final QName _TaxBase_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "TaxBase");

    private static final QName _TaxExemptionCode_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "TaxExemptionCode");

    private static final QName _TaxExemptionReason_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "TaxExemptionReason");

    private static final QName _TaxPayable_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "TaxPayable");

    private static final QName _TaxPointDate_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "TaxPointDate");

    private static final QName _TaxVerificationDate_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "TaxVerificationDate");

    private static final QName _TotalQuantityIssued_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "TotalQuantityIssued");

    private static final QName _TransactionDate_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "TransactionDate");

    private static final QName _TransactionID_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "TransactionID");

    private static final QName _UnitOfMeasure_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "UnitOfMeasure");

    private static final QName _UnitPrice_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "UnitPrice");

    private static final QName _UNNumber_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "UNNumber");

    private static final QName _WarehouseID_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "WarehouseID");

    private static final QName _WorkDate_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "WorkDate");

    private static final QName _WorkStatusDate_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "WorkStatusDate");

    private static final QName _CashVATSchemeIndicator_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "CashVATSchemeIndicator");

    private static final QName _Country_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "Country");

    private static final QName _CurrencyCode_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "CurrencyCode");

    private static final QName _DocumentNumber_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "DocumentNumber");

    private static final QName _EACCode_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "EACCode");

    private static final QName _GroupingCategory_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "GroupingCategory");

    private static final QName _InvoiceNo_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "InvoiceNo");

    private static final QName _InvoiceStatus_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "InvoiceStatus");

    private static final QName _InvoiceType_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "InvoiceType");

    private static final QName _MovementStatus_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "MovementStatus");

    private static final QName _MovementType_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "MovementType");

    private static final QName _PaymentMechanism_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "PaymentMechanism");

    private static final QName _PaymentRefNo_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "PaymentRefNo");

    private static final QName _PaymentStatus_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "PaymentStatus");

    private static final QName _Period_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "Period");

    private static final QName _TaxCode_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "TaxCode");

    private static final QName _ThirdPartiesBillingIndicator_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "ThirdPartiesBillingIndicator");

    private static final QName _TransactionType_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "TransactionType");

    private static final QName _WithholdingTaxType_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "WithholdingTaxType");

    private static final QName _WorkStatus_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "WorkStatus");

    private static final QName _WorkType_QNAME = new QName("urn:OECD:StandardAuditFile-Tax:PT_1.04_01", "WorkType");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: com.premiumminds.billy.portugal.services.export.saftpt.v1_04_01.schema
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link AuditFile}
     */
    public AuditFile createAuditFile() {
        return new AuditFile();
    }

    /**
     * Create an instance of {@link GeneralLedgerEntries}
     */
    public GeneralLedgerEntries createGeneralLedgerEntries() {
        return new GeneralLedgerEntries();
    }

    /**
     * Create an instance of {@link SourceDocuments}
     */
    public SourceDocuments createSourceDocuments() {
        return new SourceDocuments();
    }

    /**
     * Create an instance of {@link GeneralLedgerAccounts}
     */
    public GeneralLedgerAccounts createGeneralLedgerAccounts() {
        return new GeneralLedgerAccounts();
    }

    /**
     * Create an instance of {@link SourceDocuments.Payments}
     */
    public SourceDocuments.Payments createSourceDocumentsPayments() {
        return new SourceDocuments.Payments();
    }

    /**
     * Create an instance of {@link SourceDocuments.Payments.Payment}
     */
    public SourceDocuments.Payments.Payment createSourceDocumentsPaymentsPayment() {
        return new SourceDocuments.Payments.Payment();
    }

    /**
     * Create an instance of {@link SourceDocuments.Payments.Payment.DocumentTotals}
     */
    public SourceDocuments.Payments.Payment.DocumentTotals createSourceDocumentsPaymentsPaymentDocumentTotals() {
        return new SourceDocuments.Payments.Payment.DocumentTotals();
    }

    /**
     * Create an instance of {@link SourceDocuments.Payments.Payment.Line}
     */
    public SourceDocuments.Payments.Payment.Line createSourceDocumentsPaymentsPaymentLine() {
        return new SourceDocuments.Payments.Payment.Line();
    }

    /**
     * Create an instance of {@link SourceDocuments.WorkingDocuments}
     */
    public SourceDocuments.WorkingDocuments createSourceDocumentsWorkingDocuments() {
        return new SourceDocuments.WorkingDocuments();
    }

    /**
     * Create an instance of {@link SourceDocuments.WorkingDocuments.WorkDocument}
     */
    public SourceDocuments.WorkingDocuments.WorkDocument createSourceDocumentsWorkingDocumentsWorkDocument() {
        return new SourceDocuments.WorkingDocuments.WorkDocument();
    }

    /**
     * Create an instance of {@link SourceDocuments.MovementOfGoods}
     */
    public SourceDocuments.MovementOfGoods createSourceDocumentsMovementOfGoods() {
        return new SourceDocuments.MovementOfGoods();
    }

    /**
     * Create an instance of {@link SourceDocuments.MovementOfGoods.StockMovement}
     */
    public SourceDocuments.MovementOfGoods.StockMovement createSourceDocumentsMovementOfGoodsStockMovement() {
        return new SourceDocuments.MovementOfGoods.StockMovement();
    }

    /**
     * Create an instance of {@link SourceDocuments.SalesInvoices}
     */
    public SourceDocuments.SalesInvoices createSourceDocumentsSalesInvoices() {
        return new SourceDocuments.SalesInvoices();
    }

    /**
     * Create an instance of {@link SourceDocuments.SalesInvoices.Invoice}
     */
    public SourceDocuments.SalesInvoices.Invoice createSourceDocumentsSalesInvoicesInvoice() {
        return new SourceDocuments.SalesInvoices.Invoice();
    }

    /**
     * Create an instance of {@link GeneralLedgerEntries.Journal}
     */
    public GeneralLedgerEntries.Journal createGeneralLedgerEntriesJournal() {
        return new GeneralLedgerEntries.Journal();
    }

    /**
     * Create an instance of {@link GeneralLedgerEntries.Journal.Transaction}
     */
    public GeneralLedgerEntries.Journal.Transaction createGeneralLedgerEntriesJournalTransaction() {
        return new GeneralLedgerEntries.Journal.Transaction();
    }

    /**
     * Create an instance of {@link GeneralLedgerEntries.Journal.Transaction.Lines}
     */
    public GeneralLedgerEntries.Journal.Transaction.Lines createGeneralLedgerEntriesJournalTransactionLines() {
        return new GeneralLedgerEntries.Journal.Transaction.Lines();
    }

    /**
     * Create an instance of {@link Header}
     */
    public Header createHeader() {
        return new Header();
    }

    /**
     * Create an instance of {@link AddressStructurePT}
     */
    public AddressStructurePT createAddressStructurePT() {
        return new AddressStructurePT();
    }

    /**
     * Create an instance of {@link AuditFile.MasterFiles}
     */
    public AuditFile.MasterFiles createAuditFileMasterFiles() {
        return new AuditFile.MasterFiles();
    }

    /**
     * Create an instance of {@link GeneralLedgerAccounts.Account}
     */
    public GeneralLedgerAccounts.Account createGeneralLedgerAccountsAccount() {
        return new GeneralLedgerAccounts.Account();
    }

    /**
     * Create an instance of {@link Customer}
     */
    public Customer createCustomer() {
        return new Customer();
    }

    /**
     * Create an instance of {@link AddressStructure}
     */
    public AddressStructure createAddressStructure() {
        return new AddressStructure();
    }

    /**
     * Create an instance of {@link Supplier}
     */
    public Supplier createSupplier() {
        return new Supplier();
    }

    /**
     * Create an instance of {@link SupplierAddressStructure}
     */
    public SupplierAddressStructure createSupplierAddressStructure() {
        return new SupplierAddressStructure();
    }

    /**
     * Create an instance of {@link Product}
     */
    public Product createProduct() {
        return new Product();
    }

    /**
     * Create an instance of {@link CustomsDetails}
     */
    public CustomsDetails createCustomsDetails() {
        return new CustomsDetails();
    }

    /**
     * Create an instance of {@link TaxTable}
     */
    public TaxTable createTaxTable() {
        return new TaxTable();
    }

    /**
     * Create an instance of {@link TaxTableEntry}
     */
    public TaxTableEntry createTaxTableEntry() {
        return new TaxTableEntry();
    }

    /**
     * Create an instance of {@link ShippingPointStructure}
     */
    public ShippingPointStructure createShippingPointStructure() {
        return new ShippingPointStructure();
    }

    /**
     * Create an instance of {@link Currency}
     */
    public Currency createCurrency() {
        return new Currency();
    }

    /**
     * Create an instance of {@link CustomsInformation}
     */
    public CustomsInformation createCustomsInformation() {
        return new CustomsInformation();
    }

    /**
     * Create an instance of {@link MovementTax}
     */
    public MovementTax createMovementTax() {
        return new MovementTax();
    }

    /**
     * Create an instance of {@link OrderReferences}
     */
    public OrderReferences createOrderReferences() {
        return new OrderReferences();
    }

    /**
     * Create an instance of {@link PaymentMethod}
     */
    public PaymentMethod createPaymentMethod() {
        return new PaymentMethod();
    }

    /**
     * Create an instance of {@link PaymentTax}
     */
    public PaymentTax createPaymentTax() {
        return new PaymentTax();
    }

    /**
     * Create an instance of {@link ProductSerialNumber}
     */
    public ProductSerialNumber createProductSerialNumber() {
        return new ProductSerialNumber();
    }

    /**
     * Create an instance of {@link References}
     */
    public References createReferences() {
        return new References();
    }

    /**
     * Create an instance of {@link com.premiumminds.billy.portugal.services.export.saftpt.v1_04_01.schema.Settlement}
     */
    public Settlement createSettlement() {
        return new Settlement();
    }

    /**
     * Create an instance of {@link SpecialRegimes}
     */
    public SpecialRegimes createSpecialRegimes() {
        return new SpecialRegimes();
    }

    /**
     * Create an instance of {@link Tax}
     */
    public Tax createTax() {
        return new Tax();
    }

    /**
     * Create an instance of {@link WithholdingTax}
     */
    public WithholdingTax createWithholdingTax() {
        return new WithholdingTax();
    }

    /**
     * Create an instance of {@link SourceDocuments.Payments.Payment.DocumentStatus}
     */
    public SourceDocuments.Payments.Payment.DocumentStatus createSourceDocumentsPaymentsPaymentDocumentStatus() {
        return new SourceDocuments.Payments.Payment.DocumentStatus();
    }

    /**
     * Create an instance of {@link SourceDocuments.Payments.Payment.DocumentTotals.Settlement}
     */
    public SourceDocuments.Payments.Payment.DocumentTotals.Settlement createSourceDocumentsPaymentsPaymentDocumentTotalsSettlement() {
        return new SourceDocuments.Payments.Payment.DocumentTotals.Settlement();
    }

    /**
     * Create an instance of {@link SourceDocuments.Payments.Payment.Line.SourceDocumentID}
     */
    public SourceDocuments.Payments.Payment.Line.SourceDocumentID createSourceDocumentsPaymentsPaymentLineSourceDocumentID() {
        return new SourceDocuments.Payments.Payment.Line.SourceDocumentID();
    }

    /**
     * Create an instance of {@link SourceDocuments.WorkingDocuments.WorkDocument.DocumentStatus}
     */
    public SourceDocuments.WorkingDocuments.WorkDocument.DocumentStatus createSourceDocumentsWorkingDocumentsWorkDocumentDocumentStatus() {
        return new SourceDocuments.WorkingDocuments.WorkDocument.DocumentStatus();
    }

    /**
     * Create an instance of {@link SourceDocuments.WorkingDocuments.WorkDocument.Line}
     */
    public SourceDocuments.WorkingDocuments.WorkDocument.Line createSourceDocumentsWorkingDocumentsWorkDocumentLine() {
        return new SourceDocuments.WorkingDocuments.WorkDocument.Line();
    }

    /**
     * Create an instance of {@link SourceDocuments.WorkingDocuments.WorkDocument.DocumentTotals}
     */
    public SourceDocuments.WorkingDocuments.WorkDocument.DocumentTotals createSourceDocumentsWorkingDocumentsWorkDocumentDocumentTotals() {
        return new SourceDocuments.WorkingDocuments.WorkDocument.DocumentTotals();
    }

    /**
     * Create an instance of {@link SourceDocuments.MovementOfGoods.StockMovement.DocumentStatus}
     */
    public SourceDocuments.MovementOfGoods.StockMovement.DocumentStatus createSourceDocumentsMovementOfGoodsStockMovementDocumentStatus() {
        return new SourceDocuments.MovementOfGoods.StockMovement.DocumentStatus();
    }

    /**
     * Create an instance of {@link SourceDocuments.MovementOfGoods.StockMovement.Line}
     */
    public SourceDocuments.MovementOfGoods.StockMovement.Line createSourceDocumentsMovementOfGoodsStockMovementLine() {
        return new SourceDocuments.MovementOfGoods.StockMovement.Line();
    }

    /**
     * Create an instance of {@link SourceDocuments.MovementOfGoods.StockMovement.DocumentTotals}
     */
    public SourceDocuments.MovementOfGoods.StockMovement.DocumentTotals createSourceDocumentsMovementOfGoodsStockMovementDocumentTotals() {
        return new SourceDocuments.MovementOfGoods.StockMovement.DocumentTotals();
    }

    /**
     * Create an instance of {@link SourceDocuments.SalesInvoices.Invoice.DocumentStatus}
     */
    public SourceDocuments.SalesInvoices.Invoice.DocumentStatus createSourceDocumentsSalesInvoicesInvoiceDocumentStatus() {
        return new SourceDocuments.SalesInvoices.Invoice.DocumentStatus();
    }

    /**
     * Create an instance of {@link SourceDocuments.SalesInvoices.Invoice.Line}
     */
    public SourceDocuments.SalesInvoices.Invoice.Line createSourceDocumentsSalesInvoicesInvoiceLine() {
        return new SourceDocuments.SalesInvoices.Invoice.Line();
    }

    /**
     * Create an instance of {@link SourceDocuments.SalesInvoices.Invoice.DocumentTotals}
     */
    public SourceDocuments.SalesInvoices.Invoice.DocumentTotals createSourceDocumentsSalesInvoicesInvoiceDocumentTotals() {
        return new SourceDocuments.SalesInvoices.Invoice.DocumentTotals();
    }

    /**
     * Create an instance of {@link GeneralLedgerEntries.Journal.Transaction.Lines.DebitLine}
     */
    public GeneralLedgerEntries.Journal.Transaction.Lines.DebitLine createGeneralLedgerEntriesJournalTransactionLinesDebitLine() {
        return new GeneralLedgerEntries.Journal.Transaction.Lines.DebitLine();
    }

    /**
     * Create an instance of {@link GeneralLedgerEntries.Journal.Transaction.Lines.CreditLine}
     */
    public GeneralLedgerEntries.Journal.Transaction.Lines.CreditLine createGeneralLedgerEntriesJournalTransactionLinesCreditLine() {
        return new GeneralLedgerEntries.Journal.Transaction.Lines.CreditLine();
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "AuditFileVersion")
    public JAXBElement<String> createAuditFileVersion(String value) {
        return new JAXBElement<String>(_AuditFileVersion_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "CompanyID")
    public JAXBElement<String> createCompanyID(String value) {
        return new JAXBElement<String>(_CompanyID_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "TaxAccountingBasis")
    public JAXBElement<String> createTaxAccountingBasis(String value) {
        return new JAXBElement<String>(_TaxAccountingBasis_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "CompanyName")
    public JAXBElement<String> createCompanyName(String value) {
        return new JAXBElement<String>(_CompanyName_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "BusinessName")
    public JAXBElement<String> createBusinessName(String value) {
        return new JAXBElement<String>(_BusinessName_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link AddressStructurePT}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link AddressStructurePT}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "CompanyAddress")
    public JAXBElement<AddressStructurePT> createCompanyAddress(AddressStructurePT value) {
        return new JAXBElement<AddressStructurePT>(_CompanyAddress_QNAME, AddressStructurePT.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link Integer}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link Integer}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "FiscalYear")
    public JAXBElement<Integer> createFiscalYear(Integer value) {
        return new JAXBElement<Integer>(_FiscalYear_QNAME, Integer.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "StartDate")
    public JAXBElement<XMLGregorianCalendar> createStartDate(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_StartDate_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "EndDate")
    public JAXBElement<XMLGregorianCalendar> createEndDate(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_EndDate_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "DateCreated")
    public JAXBElement<XMLGregorianCalendar> createDateCreated(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_DateCreated_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "TaxEntity")
    public JAXBElement<String> createTaxEntity(String value) {
        return new JAXBElement<String>(_TaxEntity_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "ProductCompanyTaxID")
    public JAXBElement<String> createProductCompanyTaxID(String value) {
        return new JAXBElement<String>(_ProductCompanyTaxID_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link BigInteger}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link BigInteger}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "SoftwareCertificateNumber")
    public JAXBElement<BigInteger> createSoftwareCertificateNumber(BigInteger value) {
        return new JAXBElement<BigInteger>(_SoftwareCertificateNumber_QNAME, BigInteger.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "ProductID")
    public JAXBElement<String> createProductID(String value) {
        return new JAXBElement<String>(_ProductID_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "ProductVersion")
    public JAXBElement<String> createProductVersion(String value) {
        return new JAXBElement<String>(_ProductVersion_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "HeaderComment")
    public JAXBElement<String> createHeaderComment(String value) {
        return new JAXBElement<String>(_HeaderComment_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "Telephone")
    public JAXBElement<String> createTelephone(String value) {
        return new JAXBElement<String>(_Telephone_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "Fax")
    public JAXBElement<String> createFax(String value) {
        return new JAXBElement<String>(_Fax_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "Email")
    public JAXBElement<String> createEmail(String value) {
        return new JAXBElement<String>(_Email_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "Website")
    public JAXBElement<String> createWebsite(String value) {
        return new JAXBElement<String>(_Website_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link BigInteger}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link BigInteger}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "NumberOfEntries")
    public JAXBElement<BigInteger> createNumberOfEntries(BigInteger value) {
        return new JAXBElement<BigInteger>(_NumberOfEntries_QNAME, BigInteger.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "TotalDebit")
    public JAXBElement<BigDecimal> createTotalDebit(BigDecimal value) {
        return new JAXBElement<BigDecimal>(_TotalDebit_QNAME, BigDecimal.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "TotalCredit")
    public JAXBElement<BigDecimal> createTotalCredit(BigDecimal value) {
        return new JAXBElement<BigDecimal>(_TotalCredit_QNAME, BigDecimal.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "TaxonomyReference")
    public JAXBElement<String> createTaxonomyReference(String value) {
        return new JAXBElement<String>(_TaxonomyReference_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "CustomerID")
    public JAXBElement<String> createCustomerID(String value) {
        return new JAXBElement<String>(_CustomerID_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "AccountID")
    public JAXBElement<String> createAccountID(String value) {
        return new JAXBElement<String>(_AccountID_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "CustomerTaxID")
    public JAXBElement<String> createCustomerTaxID(String value) {
        return new JAXBElement<String>(_CustomerTaxID_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "Contact")
    public JAXBElement<String> createContact(String value) {
        return new JAXBElement<String>(_Contact_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link AddressStructure}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link AddressStructure}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "BillingAddress")
    public JAXBElement<AddressStructure> createBillingAddress(AddressStructure value) {
        return new JAXBElement<AddressStructure>(_BillingAddress_QNAME, AddressStructure.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link AddressStructure}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link AddressStructure}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "ShipToAddress")
    public JAXBElement<AddressStructure> createShipToAddress(AddressStructure value) {
        return new JAXBElement<AddressStructure>(_ShipToAddress_QNAME, AddressStructure.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link Integer}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link Integer}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "SelfBillingIndicator")
    public JAXBElement<Integer> createSelfBillingIndicator(Integer value) {
        return new JAXBElement<Integer>(_SelfBillingIndicator_QNAME, Integer.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "SupplierID")
    public JAXBElement<String> createSupplierID(String value) {
        return new JAXBElement<String>(_SupplierID_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "SupplierTaxID")
    public JAXBElement<String> createSupplierTaxID(String value) {
        return new JAXBElement<String>(_SupplierTaxID_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "ProductType")
    public JAXBElement<String> createProductType(String value) {
        return new JAXBElement<String>(_ProductType_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "ProductCode")
    public JAXBElement<String> createProductCode(String value) {
        return new JAXBElement<String>(_ProductCode_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "ProductGroup")
    public JAXBElement<String> createProductGroup(String value) {
        return new JAXBElement<String>(_ProductGroup_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "ProductDescription")
    public JAXBElement<String> createProductDescription(String value) {
        return new JAXBElement<String>(_ProductDescription_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "ProductNumberCode")
    public JAXBElement<String> createProductNumberCode(String value) {
        return new JAXBElement<String>(_ProductNumberCode_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "TaxType")
    public JAXBElement<String> createTaxType(String value) {
        return new JAXBElement<String>(_TaxType_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "TaxCountryRegion")
    public JAXBElement<String> createTaxCountryRegion(String value) {
        return new JAXBElement<String>(_TaxCountryRegion_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "TaxExpirationDate")
    public JAXBElement<XMLGregorianCalendar> createTaxExpirationDate(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_TaxExpirationDate_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "TaxPercentage")
    public JAXBElement<BigDecimal> createTaxPercentage(BigDecimal value) {
        return new JAXBElement<BigDecimal>(_TaxPercentage_QNAME, BigDecimal.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "TaxAmount")
    public JAXBElement<BigDecimal> createTaxAmount(BigDecimal value) {
        return new JAXBElement<BigDecimal>(_TaxAmount_QNAME, BigDecimal.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "AccountDescription")
    public JAXBElement<String> createAccountDescription(String value) {
        return new JAXBElement<String>(_AccountDescription_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link AddressStructure}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link AddressStructure}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "Address")
    public JAXBElement<AddressStructure> createAddress(AddressStructure value) {
        return new JAXBElement<AddressStructure>(_Address_QNAME, AddressStructure.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "AddressDetail")
    public JAXBElement<String> createAddressDetail(String value) {
        return new JAXBElement<String>(_AddressDetail_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "ARCNo")
    public JAXBElement<String> createARCNo(String value) {
        return new JAXBElement<String>(_ARCNo_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "ATCUD")
    public JAXBElement<String> createATCUD(String value) {
        return new JAXBElement<String>(_ATCUD_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "ATDocCodeID")
    public JAXBElement<String> createATDocCodeID(String value) {
        return new JAXBElement<String>(_ATDocCodeID_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "BuildingNumber")
    public JAXBElement<String> createBuildingNumber(String value) {
        return new JAXBElement<String>(_BuildingNumber_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "City")
    public JAXBElement<String> createCity(String value) {
        return new JAXBElement<String>(_City_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "ClosingCreditBalance")
    public JAXBElement<BigDecimal> createClosingCreditBalance(BigDecimal value) {
        return new JAXBElement<BigDecimal>(_ClosingCreditBalance_QNAME, BigDecimal.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "ClosingDebitBalance")
    public JAXBElement<BigDecimal> createClosingDebitBalance(BigDecimal value) {
        return new JAXBElement<BigDecimal>(_ClosingDebitBalance_QNAME, BigDecimal.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "CNCode")
    public JAXBElement<String> createCNCode(String value) {
        return new JAXBElement<String>(_CNCode_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "CreditAmount")
    public JAXBElement<BigDecimal> createCreditAmount(BigDecimal value) {
        return new JAXBElement<BigDecimal>(_CreditAmount_QNAME, BigDecimal.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "CurrencyAmount")
    public JAXBElement<BigDecimal> createCurrencyAmount(BigDecimal value) {
        return new JAXBElement<BigDecimal>(_CurrencyAmount_QNAME, BigDecimal.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "DebitAmount")
    public JAXBElement<BigDecimal> createDebitAmount(BigDecimal value) {
        return new JAXBElement<BigDecimal>(_DebitAmount_QNAME, BigDecimal.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "DeliveryDate")
    public JAXBElement<XMLGregorianCalendar> createDeliveryDate(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_DeliveryDate_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "DeliveryID")
    public JAXBElement<String> createDeliveryID(String value) {
        return new JAXBElement<String>(_DeliveryID_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "Description")
    public JAXBElement<String> createDescription(String value) {
        return new JAXBElement<String>(_Description_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "DocArchivalNumber")
    public JAXBElement<String> createDocArchivalNumber(String value) {
        return new JAXBElement<String>(_DocArchivalNumber_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "ExchangeRate")
    public JAXBElement<BigDecimal> createExchangeRate(BigDecimal value) {
        return new JAXBElement<BigDecimal>(_ExchangeRate_QNAME, BigDecimal.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "GLPostingDate")
    public JAXBElement<XMLGregorianCalendar> createGLPostingDate(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_GLPostingDate_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "GrossTotal")
    public JAXBElement<BigDecimal> createGrossTotal(BigDecimal value) {
        return new JAXBElement<BigDecimal>(_GrossTotal_QNAME, BigDecimal.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "Hash")
    public JAXBElement<String> createHash(String value) {
        return new JAXBElement<String>(_Hash_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "HashControl")
    public JAXBElement<String> createHashControl(String value) {
        return new JAXBElement<String>(_HashControl_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "IECAmount")
    public JAXBElement<BigDecimal> createIECAmount(BigDecimal value) {
        return new JAXBElement<BigDecimal>(_IECAmount_QNAME, BigDecimal.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "InvoiceDate")
    public JAXBElement<XMLGregorianCalendar> createInvoiceDate(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_InvoiceDate_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "InvoiceStatusDate")
    public JAXBElement<XMLGregorianCalendar> createInvoiceStatusDate(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_InvoiceStatusDate_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "JournalID")
    public JAXBElement<String> createJournalID(String value) {
        return new JAXBElement<String>(_JournalID_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link BigInteger}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link BigInteger}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "LineNumber")
    public JAXBElement<BigInteger> createLineNumber(BigInteger value) {
        return new JAXBElement<BigInteger>(_LineNumber_QNAME, BigInteger.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "LocationID")
    public JAXBElement<String> createLocationID(String value) {
        return new JAXBElement<String>(_LocationID_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "MovementComments")
    public JAXBElement<String> createMovementComments(String value) {
        return new JAXBElement<String>(_MovementComments_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "MovementDate")
    public JAXBElement<XMLGregorianCalendar> createMovementDate(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_MovementDate_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "MovementEndTime")
    public JAXBElement<XMLGregorianCalendar> createMovementEndTime(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_MovementEndTime_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "MovementStartTime")
    public JAXBElement<XMLGregorianCalendar> createMovementStartTime(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_MovementStartTime_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "MovementStatusDate")
    public JAXBElement<XMLGregorianCalendar> createMovementStatusDate(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_MovementStatusDate_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "NetTotal")
    public JAXBElement<BigDecimal> createNetTotal(BigDecimal value) {
        return new JAXBElement<BigDecimal>(_NetTotal_QNAME, BigDecimal.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link BigInteger}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link BigInteger}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "NumberOfMovementLines")
    public JAXBElement<BigInteger> createNumberOfMovementLines(BigInteger value) {
        return new JAXBElement<BigInteger>(_NumberOfMovementLines_QNAME, BigInteger.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "OpeningCreditBalance")
    public JAXBElement<BigDecimal> createOpeningCreditBalance(BigDecimal value) {
        return new JAXBElement<BigDecimal>(_OpeningCreditBalance_QNAME, BigDecimal.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "OpeningDebitBalance")
    public JAXBElement<BigDecimal> createOpeningDebitBalance(BigDecimal value) {
        return new JAXBElement<BigDecimal>(_OpeningDebitBalance_QNAME, BigDecimal.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "OrderDate")
    public JAXBElement<XMLGregorianCalendar> createOrderDate(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_OrderDate_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "OriginatingON")
    public JAXBElement<String> createOriginatingON(String value) {
        return new JAXBElement<String>(_OriginatingON_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "PaymentStatusDate")
    public JAXBElement<XMLGregorianCalendar> createPaymentStatusDate(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_PaymentStatusDate_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "PostalCode")
    public JAXBElement<String> createPostalCode(String value) {
        return new JAXBElement<String>(_PostalCode_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "Quantity")
    public JAXBElement<BigDecimal> createQuantity(BigDecimal value) {
        return new JAXBElement<BigDecimal>(_Quantity_QNAME, BigDecimal.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "Reason")
    public JAXBElement<String> createReason(String value) {
        return new JAXBElement<String>(_Reason_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "RecordID")
    public JAXBElement<String> createRecordID(String value) {
        return new JAXBElement<String>(_RecordID_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "Reference")
    public JAXBElement<String> createReference(String value) {
        return new JAXBElement<String>(_Reference_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "Region")
    public JAXBElement<String> createRegion(String value) {
        return new JAXBElement<String>(_Region_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "SerialNumber")
    public JAXBElement<String> createSerialNumber(String value) {
        return new JAXBElement<String>(_SerialNumber_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "SettlementAmount")
    public JAXBElement<BigDecimal> createSettlementAmount(BigDecimal value) {
        return new JAXBElement<BigDecimal>(_SettlementAmount_QNAME, BigDecimal.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link ShippingPointStructure}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link ShippingPointStructure}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "ShipFrom")
    public JAXBElement<ShippingPointStructure> createShipFrom(ShippingPointStructure value) {
        return new JAXBElement<ShippingPointStructure>(_ShipFrom_QNAME, ShippingPointStructure.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link AddressStructure}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link AddressStructure}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "ShipFromAddress")
    public JAXBElement<AddressStructure> createShipFromAddress(AddressStructure value) {
        return new JAXBElement<AddressStructure>(_ShipFromAddress_QNAME, AddressStructure.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link ShippingPointStructure}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link ShippingPointStructure}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "ShipTo")
    public JAXBElement<ShippingPointStructure> createShipTo(ShippingPointStructure value) {
        return new JAXBElement<ShippingPointStructure>(_ShipTo_QNAME, ShippingPointStructure.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "SourceDocumentID")
    public JAXBElement<String> createSourceDocumentID(String value) {
        return new JAXBElement<String>(_SourceDocumentID_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "SourceID")
    public JAXBElement<String> createSourceID(String value) {
        return new JAXBElement<String>(_SourceID_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "StreetName")
    public JAXBElement<String> createStreetName(String value) {
        return new JAXBElement<String>(_StreetName_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "SystemEntryDate")
    public JAXBElement<XMLGregorianCalendar> createSystemEntryDate(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_SystemEntryDate_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "SystemID")
    public JAXBElement<String> createSystemID(String value) {
        return new JAXBElement<String>(_SystemID_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "TaxBase")
    public JAXBElement<BigDecimal> createTaxBase(BigDecimal value) {
        return new JAXBElement<BigDecimal>(_TaxBase_QNAME, BigDecimal.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "TaxExemptionCode")
    public JAXBElement<String> createTaxExemptionCode(String value) {
        return new JAXBElement<String>(_TaxExemptionCode_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "TaxExemptionReason")
    public JAXBElement<String> createTaxExemptionReason(String value) {
        return new JAXBElement<String>(_TaxExemptionReason_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "TaxPayable")
    public JAXBElement<BigDecimal> createTaxPayable(BigDecimal value) {
        return new JAXBElement<BigDecimal>(_TaxPayable_QNAME, BigDecimal.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "TaxPointDate")
    public JAXBElement<XMLGregorianCalendar> createTaxPointDate(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_TaxPointDate_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "TaxVerificationDate")
    public JAXBElement<XMLGregorianCalendar> createTaxVerificationDate(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_TaxVerificationDate_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "TotalQuantityIssued")
    public JAXBElement<BigDecimal> createTotalQuantityIssued(BigDecimal value) {
        return new JAXBElement<BigDecimal>(_TotalQuantityIssued_QNAME, BigDecimal.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "TransactionDate")
    public JAXBElement<XMLGregorianCalendar> createTransactionDate(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_TransactionDate_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "TransactionID")
    public JAXBElement<String> createTransactionID(String value) {
        return new JAXBElement<String>(_TransactionID_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "UnitOfMeasure")
    public JAXBElement<String> createUnitOfMeasure(String value) {
        return new JAXBElement<String>(_UnitOfMeasure_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link BigDecimal}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "UnitPrice")
    public JAXBElement<BigDecimal> createUnitPrice(BigDecimal value) {
        return new JAXBElement<BigDecimal>(_UnitPrice_QNAME, BigDecimal.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "UNNumber")
    public JAXBElement<String> createUNNumber(String value) {
        return new JAXBElement<String>(_UNNumber_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "WarehouseID")
    public JAXBElement<String> createWarehouseID(String value) {
        return new JAXBElement<String>(_WarehouseID_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "WorkDate")
    public JAXBElement<XMLGregorianCalendar> createWorkDate(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_WorkDate_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link XMLGregorianCalendar}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "WorkStatusDate")
    public JAXBElement<XMLGregorianCalendar> createWorkStatusDate(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_WorkStatusDate_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link Integer}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link Integer}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "CashVATSchemeIndicator")
    public JAXBElement<Integer> createCashVATSchemeIndicator(Integer value) {
        return new JAXBElement<Integer>(_CashVATSchemeIndicator_QNAME, Integer.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "Country")
    public JAXBElement<String> createCountry(String value) {
        return new JAXBElement<String>(_Country_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "CurrencyCode")
    public JAXBElement<String> createCurrencyCode(String value) {
        return new JAXBElement<String>(_CurrencyCode_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "DocumentNumber")
    public JAXBElement<String> createDocumentNumber(String value) {
        return new JAXBElement<String>(_DocumentNumber_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "EACCode")
    public JAXBElement<String> createEACCode(String value) {
        return new JAXBElement<String>(_EACCode_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "GroupingCategory")
    public JAXBElement<String> createGroupingCategory(String value) {
        return new JAXBElement<String>(_GroupingCategory_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "InvoiceNo")
    public JAXBElement<String> createInvoiceNo(String value) {
        return new JAXBElement<String>(_InvoiceNo_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "InvoiceStatus")
    public JAXBElement<String> createInvoiceStatus(String value) {
        return new JAXBElement<String>(_InvoiceStatus_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "InvoiceType")
    public JAXBElement<String> createInvoiceType(String value) {
        return new JAXBElement<String>(_InvoiceType_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "MovementStatus")
    public JAXBElement<String> createMovementStatus(String value) {
        return new JAXBElement<String>(_MovementStatus_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "MovementType")
    public JAXBElement<String> createMovementType(String value) {
        return new JAXBElement<String>(_MovementType_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "PaymentMechanism")
    public JAXBElement<String> createPaymentMechanism(String value) {
        return new JAXBElement<String>(_PaymentMechanism_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "PaymentRefNo")
    public JAXBElement<String> createPaymentRefNo(String value) {
        return new JAXBElement<String>(_PaymentRefNo_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "PaymentStatus")
    public JAXBElement<String> createPaymentStatus(String value) {
        return new JAXBElement<String>(_PaymentStatus_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link Integer}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link Integer}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "Period")
    public JAXBElement<Integer> createPeriod(Integer value) {
        return new JAXBElement<Integer>(_Period_QNAME, Integer.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "TaxCode")
    public JAXBElement<String> createTaxCode(String value) {
        return new JAXBElement<String>(_TaxCode_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link Integer}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link Integer}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "ThirdPartiesBillingIndicator")
    public JAXBElement<Integer> createThirdPartiesBillingIndicator(Integer value) {
        return new JAXBElement<Integer>(_ThirdPartiesBillingIndicator_QNAME, Integer.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "TransactionType")
    public JAXBElement<String> createTransactionType(String value) {
        return new JAXBElement<String>(_TransactionType_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "WithholdingTaxType")
    public JAXBElement<String> createWithholdingTaxType(String value) {
        return new JAXBElement<String>(_WithholdingTaxType_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "WorkStatus")
    public JAXBElement<String> createWorkStatus(String value) {
        return new JAXBElement<String>(_WorkStatus_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     *
     * @param value
     * 		Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement}{@code <}{@link String}{@code >}
     */
    @XmlElementDecl(namespace = "urn:OECD:StandardAuditFile-Tax:PT_1.04_01", name = "WorkType")
    public JAXBElement<String> createWorkType(String value) {
        return new JAXBElement<String>(_WorkType_QNAME, String.class, null, value);
    }
}
