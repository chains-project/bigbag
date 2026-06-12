package com.premiumminds.billy.portugal.persistence.entities.jpa;
import EntityPathBase;
import JPAPTSimpleInvoiceEntity;
import com.premiumminds.billy.core.services.entities.documents.GenericInvoice.CreditOrDebit;
import com.premiumminds.billy.persistence.entities.jpa.QJPABusinessEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPACustomerEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPAShippingPointEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPASupplierEntity;
import com.premiumminds.billy.portugal.services.entities.PTGenericInvoice.SourceBilling;
import com.premiumminds.billy.portugal.services.entities.PTGenericInvoice.TYPE;
import com.premiumminds.billy.portugal.services.entities.PTInvoiceEntry;
import com.premiumminds.billy.portugal.services.entities.PTPayment;
import com.premiumminds.billy.portugal.services.entities.PTSimpleInvoice.CLIENTTYPE;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Date;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.types.dsl.*;
import com.querydsl.core.types.dsl.PathInits;
import static com.querydsl.core.types.PathMetadataFactory.*;
/**
 * QJPAPTSimpleInvoiceEntity is a Querydsl query type for JPAPTSimpleInvoiceEntity
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QJPAPTSimpleInvoiceEntity extends EntityPathBase<JPAPTSimpleInvoiceEntity> {
    private static final long serialVersionUID = -2020506413L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QJPAPTSimpleInvoiceEntity jPAPTSimpleInvoiceEntity = new QJPAPTSimpleInvoiceEntity("jPAPTSimpleInvoiceEntity");

    public final QJPAPTInvoiceEntity _super;

    // inherited
    public final BooleanPath active;

    // inherited
    public final NumberPath<BigDecimal> amountWithoutTax;

    // inherited
    public final NumberPath<BigDecimal> amountWithTax;

    // inherited
    public final StringPath atcud;

    // inherited
    public final StringPath batchId;

    // inherited
    public final BooleanPath billed;

    // inherited
    public final QJPABusinessEntity business;

    // inherited
    public final BooleanPath cancelled;

    // inherited
    public final BooleanPath cashVATEndorser;

    public final EnumPath<CLIENTTYPE> clientType = createEnum("clientType", CLIENTTYPE.class);

    // inherited
    public final DateTimePath<Date> createTimestamp;

    // inherited
    public final EnumPath<CreditOrDebit> creditOrDebit;

    // inherited
    public final SimplePath<Currency> currency;

    // inherited
    public final QJPACustomerEntity customer;

    // inherited
    public final DateTimePath<Date> date;

    // inherited
    public final NumberPath<BigDecimal> discountsAmount;

    // inherited
    public final StringPath eacCode;

    // inherited
    public final NumberPath<Integer> entityVersion;

    // inherited
    public final ListPath<PTInvoiceEntry, SimplePath<PTInvoiceEntry>> entries;

    // inherited
    public final DateTimePath<Date> generalLedgerDate;

    // inherited
    public final StringPath hash;

    // inherited
    public final StringPath hashControl;

    // inherited
    public final NumberPath<Long> id;

    // inherited
    public final DatePath<LocalDate> localDate;

    // inherited
    public final StringPath number;

    // inherited
    public final StringPath officeNumber;

    // inherited
    public final ListPath<PTPayment, SimplePath<PTPayment>> payments;

    // inherited
    public final StringPath paymentTerms;

    // inherited
    public final StringPath reason;

    // inherited
    public final ListPath<String, StringPath> receiptNumbers;

    // inherited
    public final NumberPath<Integer> scale;

    // inherited
    public final BooleanPath selfBilled;

    // inherited
    public final StringPath series;

    // inherited
    public final NumberPath<Integer> seriesNumber;

    // inherited
    public final DateTimePath<Date> settlementDate;

    // inherited
    public final StringPath settlementDescription;

    // inherited
    public final NumberPath<BigDecimal> settlementDiscount;

    // inherited
    public final QJPAShippingPointEntity shippingDestination;

    // inherited
    public final QJPAShippingPointEntity shippingOrigin;

    // inherited
    public final EnumPath<SourceBilling> sourceBilling;

    // inherited
    public final StringPath sourceHash;

    // inherited
    public final StringPath sourceId;

    // inherited
    public final QJPASupplierEntity supplier;

    // inherited
    public final NumberPath<BigDecimal> taxAmount;

    // inherited
    public final BooleanPath thirdPartyBilled;

    // inherited
    public final StringPath transactionId;

    // inherited
    public final EnumPath<TYPE> type;

    // inherited
    public final StringPath uid;

    // inherited
    public final DateTimePath<Date> updateTimestamp;

    public QJPAPTSimpleInvoiceEntity(String variable) {
        this(com.premiumminds.billy.portugal.persistence.entities.jpa.JPAPTSimpleInvoiceEntity.class, forVariable(variable), INITS);
    }

    public QJPAPTSimpleInvoiceEntity(Path<? extends JPAPTSimpleInvoiceEntity> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QJPAPTSimpleInvoiceEntity(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QJPAPTSimpleInvoiceEntity(PathMetadata metadata, PathInits inits) {
        this(com.premiumminds.billy.portugal.persistence.entities.jpa.JPAPTSimpleInvoiceEntity.class, metadata, inits);
    }

    public QJPAPTSimpleInvoiceEntity(Class<? extends JPAPTSimpleInvoiceEntity> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this._super = new QJPAPTInvoiceEntity(type, metadata, inits);
        this.active = _super.active;
        this.amountWithoutTax = _super.amountWithoutTax;
        this.amountWithTax = _super.amountWithTax;
        this.atcud = _super.atcud;
        this.batchId = _super.batchId;
        this.billed = _super.billed;
        this.business = _super.business;
        this.cancelled = _super.cancelled;
        this.cashVATEndorser = _super.cashVATEndorser;
        this.createTimestamp = _super.createTimestamp;
        this.creditOrDebit = _super.creditOrDebit;
        this.currency = _super.currency;
        this.customer = _super.customer;
        this.date = _super.date;
        this.discountsAmount = _super.discountsAmount;
        this.eacCode = _super.eacCode;
        this.entityVersion = _super.entityVersion;
        this.entries = _super.entries;
        this.generalLedgerDate = _super.generalLedgerDate;
        this.hash = _super.hash;
        this.hashControl = _super.hashControl;
        this.id = _super.id;
        this.localDate = _super.localDate;
        this.number = _super.number;
        this.officeNumber = _super.officeNumber;
        this.payments = _super.payments;
        this.paymentTerms = _super.paymentTerms;
        this.reason = _super.reason;
        this.receiptNumbers = _super.receiptNumbers;
        this.scale = _super.scale;
        this.selfBilled = _super.selfBilled;
        this.series = _super.series;
        this.seriesNumber = _super.seriesNumber;
        this.settlementDate = _super.settlementDate;
        this.settlementDescription = _super.settlementDescription;
        this.settlementDiscount = _super.settlementDiscount;
        this.shippingDestination = _super.shippingDestination;
        this.shippingOrigin = _super.shippingOrigin;
        this.sourceBilling = _super.sourceBilling;
        this.sourceHash = _super.sourceHash;
        this.sourceId = _super.sourceId;
        this.supplier = _super.supplier;
        this.taxAmount = _super.taxAmount;
        this.thirdPartyBilled = _super.thirdPartyBilled;
        this.transactionId = _super.transactionId;
        this.type = _super.type;
        this.uid = _super.uid;
        this.updateTimestamp = _super.updateTimestamp;
    }
}
