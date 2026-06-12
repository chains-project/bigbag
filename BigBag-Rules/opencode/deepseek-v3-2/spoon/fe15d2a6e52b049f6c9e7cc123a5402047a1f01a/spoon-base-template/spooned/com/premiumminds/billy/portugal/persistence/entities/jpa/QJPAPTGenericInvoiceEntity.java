package com.premiumminds.billy.portugal.persistence.entities.jpa;
import EntityPathBase;
import JPAPTGenericInvoiceEntity;
import com.premiumminds.billy.core.services.entities.documents.GenericInvoice.CreditOrDebit;
import com.premiumminds.billy.persistence.entities.jpa.JPAGenericInvoiceEntryEntity;
import com.premiumminds.billy.persistence.entities.jpa.JPAPaymentEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPABusinessEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPACustomerEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPAGenericInvoiceEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPAGenericInvoiceEntryEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPAPaymentEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPAShippingPointEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPASupplierEntity;
import com.premiumminds.billy.portugal.services.entities.PTGenericInvoice.SourceBilling;
import com.premiumminds.billy.portugal.services.entities.PTGenericInvoice.TYPE;
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
 * QJPAPTGenericInvoiceEntity is a Querydsl query type for JPAPTGenericInvoiceEntity
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QJPAPTGenericInvoiceEntity extends EntityPathBase<JPAPTGenericInvoiceEntity> {
    private static final long serialVersionUID = 1557782404L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QJPAPTGenericInvoiceEntity jPAPTGenericInvoiceEntity = new QJPAPTGenericInvoiceEntity("jPAPTGenericInvoiceEntity");

    public final QJPAGenericInvoiceEntity _super;

    // inherited
    public final BooleanPath active;

    // inherited
    public final NumberPath<BigDecimal> amountWithoutTax;

    // inherited
    public final NumberPath<BigDecimal> amountWithTax;

    public final StringPath atcud = createString("atcud");

    // inherited
    public final StringPath batchId;

    public final BooleanPath billed = createBoolean("billed");

    // inherited
    public final QJPABusinessEntity business;

    public final BooleanPath cancelled = createBoolean("cancelled");

    // inherited
    public final BooleanPath cashVATEndorser;

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

    public final StringPath eacCode = createString("eacCode");

    // inherited
    public final NumberPath<Integer> entityVersion;

    // inherited
    public final ListPath<JPAGenericInvoiceEntryEntity, QJPAGenericInvoiceEntryEntity> entries;

    // inherited
    public final DateTimePath<Date> generalLedgerDate;

    public final StringPath hash = createString("hash");

    public final StringPath hashControl = createString("hashControl");

    // inherited
    public final NumberPath<Long> id;

    // inherited
    public final DatePath<LocalDate> localDate;

    // inherited
    public final StringPath number;

    // inherited
    public final StringPath officeNumber;

    // inherited
    public final ListPath<JPAPaymentEntity, QJPAPaymentEntity> payments;

    // inherited
    public final StringPath paymentTerms;

    public final StringPath reason = createString("reason");

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

    public final EnumPath<SourceBilling> sourceBilling = createEnum("sourceBilling", SourceBilling.class);

    public final StringPath sourceHash = createString("sourceHash");

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

    public final EnumPath<TYPE> type = createEnum("type", TYPE.class);

    // inherited
    public final StringPath uid;

    // inherited
    public final DateTimePath<Date> updateTimestamp;

    public QJPAPTGenericInvoiceEntity(String variable) {
        this(com.premiumminds.billy.portugal.persistence.entities.jpa.JPAPTGenericInvoiceEntity.class, forVariable(variable), INITS);
    }

    public QJPAPTGenericInvoiceEntity(Path<? extends JPAPTGenericInvoiceEntity> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QJPAPTGenericInvoiceEntity(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QJPAPTGenericInvoiceEntity(PathMetadata metadata, PathInits inits) {
        this(com.premiumminds.billy.portugal.persistence.entities.jpa.JPAPTGenericInvoiceEntity.class, metadata, inits);
    }

    public QJPAPTGenericInvoiceEntity(Class<? extends JPAPTGenericInvoiceEntity> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this._super = new QJPAGenericInvoiceEntity(type, metadata, inits);
        this.active = _super.active;
        this.amountWithoutTax = _super.amountWithoutTax;
        this.amountWithTax = _super.amountWithTax;
        this.batchId = _super.batchId;
        this.business = _super.business;
        this.cashVATEndorser = _super.cashVATEndorser;
        this.createTimestamp = _super.createTimestamp;
        this.creditOrDebit = _super.creditOrDebit;
        this.currency = _super.currency;
        this.customer = _super.customer;
        this.date = _super.date;
        this.discountsAmount = _super.discountsAmount;
        this.entityVersion = _super.entityVersion;
        this.entries = _super.entries;
        this.generalLedgerDate = _super.generalLedgerDate;
        this.id = _super.id;
        this.localDate = _super.localDate;
        this.number = _super.number;
        this.officeNumber = _super.officeNumber;
        this.payments = _super.payments;
        this.paymentTerms = _super.paymentTerms;
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
        this.sourceId = _super.sourceId;
        this.supplier = _super.supplier;
        this.taxAmount = _super.taxAmount;
        this.thirdPartyBilled = _super.thirdPartyBilled;
        this.transactionId = _super.transactionId;
        this.uid = _super.uid;
        this.updateTimestamp = _super.updateTimestamp;
    }
}
