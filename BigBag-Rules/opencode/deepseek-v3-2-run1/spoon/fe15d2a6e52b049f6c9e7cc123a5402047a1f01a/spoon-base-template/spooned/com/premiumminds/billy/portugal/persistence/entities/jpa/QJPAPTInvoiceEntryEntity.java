package com.premiumminds.billy.portugal.persistence.entities.jpa;
import EntityPathBase;
import JPAPTInvoiceEntryEntity;
import com.premiumminds.billy.core.services.builders.GenericInvoiceEntryBuilder.AmountType;
import com.premiumminds.billy.core.services.entities.documents.GenericInvoice.CreditOrDebit;
import com.premiumminds.billy.persistence.entities.jpa.JPAGenericInvoiceEntity;
import com.premiumminds.billy.persistence.entities.jpa.JPATaxEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPAGenericInvoiceEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPAProductEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPAShippingPointEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPATaxEntity;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Date;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.types.dsl.*;
import com.querydsl.core.types.dsl.PathInits;
import static com.querydsl.core.types.PathMetadataFactory.*;
/**
 * QJPAPTInvoiceEntryEntity is a Querydsl query type for JPAPTInvoiceEntryEntity
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QJPAPTInvoiceEntryEntity extends EntityPathBase<JPAPTInvoiceEntryEntity> {
    private static final long serialVersionUID = 1004078835L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QJPAPTInvoiceEntryEntity jPAPTInvoiceEntryEntity = new QJPAPTInvoiceEntryEntity("jPAPTInvoiceEntryEntity");

    public final QJPAPTGenericInvoiceEntryEntity _super;

    // inherited
    public final BooleanPath active;

    // inherited
    public final NumberPath<BigDecimal> amountWithoutTax;

    // inherited
    public final NumberPath<BigDecimal> amountWithTax;

    // inherited
    public final DateTimePath<Date> createTimestamp;

    // inherited
    public final EnumPath<CreditOrDebit> creditOrDebit;

    // inherited
    public final SimplePath<Currency> currency;

    // inherited
    public final StringPath description;

    // inherited
    public final NumberPath<BigDecimal> discountAmount;

    // inherited
    public final NumberPath<Integer> entityVersion;

    // inherited
    public final NumberPath<BigDecimal> exchangeRateToDocumentCurrency;

    // inherited
    public final NumberPath<Long> id;

    // inherited
    public final NumberPath<Integer> number;

    // inherited
    public final QJPAProductEntity product;

    // inherited
    public final NumberPath<BigDecimal> quantity;

    // inherited
    public final ListPath<JPAGenericInvoiceEntity, QJPAGenericInvoiceEntity> references;

    // inherited
    public final NumberPath<BigDecimal> shippingCostsAmount;

    // inherited
    public final QJPAShippingPointEntity shippingDestination;

    // inherited
    public final QJPAShippingPointEntity shippingOrigin;

    // inherited
    public final NumberPath<BigDecimal> taxAmount;

    // inherited
    public final ListPath<JPATaxEntity, QJPATaxEntity> taxes;

    // inherited
    public final StringPath taxExemptionCode;

    // inherited
    public final StringPath taxExemptionReason;

    // inherited
    public final DateTimePath<Date> taxPointDate;

    // inherited
    public final EnumPath<AmountType> type;

    // inherited
    public final StringPath uid;

    // inherited
    public final NumberPath<BigDecimal> unitAmountWithoutTax;

    // inherited
    public final NumberPath<BigDecimal> unitAmountWithTax;

    // inherited
    public final NumberPath<BigDecimal> unitDiscountAmount;

    // inherited
    public final StringPath unitOfMeasure;

    // inherited
    public final NumberPath<BigDecimal> unitTaxAmount;

    // inherited
    public final DateTimePath<Date> updateTimestamp;

    public QJPAPTInvoiceEntryEntity(String variable) {
        this(com.premiumminds.billy.portugal.persistence.entities.jpa.JPAPTInvoiceEntryEntity.class, forVariable(variable), INITS);
    }

    public QJPAPTInvoiceEntryEntity(Path<? extends JPAPTInvoiceEntryEntity> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QJPAPTInvoiceEntryEntity(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QJPAPTInvoiceEntryEntity(PathMetadata metadata, PathInits inits) {
        this(com.premiumminds.billy.portugal.persistence.entities.jpa.JPAPTInvoiceEntryEntity.class, metadata, inits);
    }

    public QJPAPTInvoiceEntryEntity(Class<? extends JPAPTInvoiceEntryEntity> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this._super = new QJPAPTGenericInvoiceEntryEntity(type, metadata, inits);
        this.active = _super.active;
        this.amountWithoutTax = _super.amountWithoutTax;
        this.amountWithTax = _super.amountWithTax;
        this.createTimestamp = _super.createTimestamp;
        this.creditOrDebit = _super.creditOrDebit;
        this.currency = _super.currency;
        this.description = _super.description;
        this.discountAmount = _super.discountAmount;
        this.entityVersion = _super.entityVersion;
        this.exchangeRateToDocumentCurrency = _super.exchangeRateToDocumentCurrency;
        this.id = _super.id;
        this.number = _super.number;
        this.product = _super.product;
        this.quantity = _super.quantity;
        this.references = _super.references;
        this.shippingCostsAmount = _super.shippingCostsAmount;
        this.shippingDestination = _super.shippingDestination;
        this.shippingOrigin = _super.shippingOrigin;
        this.taxAmount = _super.taxAmount;
        this.taxes = _super.taxes;
        this.taxExemptionCode = _super.taxExemptionCode;
        this.taxExemptionReason = _super.taxExemptionReason;
        this.taxPointDate = _super.taxPointDate;
        this.type = _super.type;
        this.uid = _super.uid;
        this.unitAmountWithoutTax = _super.unitAmountWithoutTax;
        this.unitAmountWithTax = _super.unitAmountWithTax;
        this.unitDiscountAmount = _super.unitDiscountAmount;
        this.unitOfMeasure = _super.unitOfMeasure;
        this.unitTaxAmount = _super.unitTaxAmount;
        this.updateTimestamp = _super.updateTimestamp;
    }
}
