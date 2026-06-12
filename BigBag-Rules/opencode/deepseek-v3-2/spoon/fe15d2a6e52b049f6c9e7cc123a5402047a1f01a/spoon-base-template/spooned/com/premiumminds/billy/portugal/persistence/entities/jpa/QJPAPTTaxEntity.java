package com.premiumminds.billy.portugal.persistence.entities.jpa;
import EntityPathBase;
import JPAPTTaxEntity;
import com.premiumminds.billy.core.services.entities.Context;
import com.premiumminds.billy.core.services.entities.Tax.TaxRateType;
import com.premiumminds.billy.persistence.entities.jpa.QJPATaxEntity;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Date;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.types.dsl.*;
import static com.querydsl.core.types.PathMetadataFactory.*;
/**
 * QJPAPTTaxEntity is a Querydsl query type for JPAPTTaxEntity
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QJPAPTTaxEntity extends EntityPathBase<JPAPTTaxEntity> {
    private static final long serialVersionUID = -688989789L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QJPAPTTaxEntity jPAPTTaxEntity = new QJPAPTTaxEntity("jPAPTTaxEntity");

    public final QJPATaxEntity _super;

    // inherited
    public final BooleanPath active;

    // inherited
    public final StringPath code;

    public final SimplePath<Context> context = createSimple("context", Context.class);

    // inherited
    public final DateTimePath<Date> createTimestamp;

    // inherited
    public final SimplePath<Currency> currency;

    // inherited
    public final StringPath description;

    // inherited
    public final StringPath designation;

    // inherited
    public final NumberPath<Integer> entityVersion;

    // inherited
    public final NumberPath<BigDecimal> flatRateAmount;

    // inherited
    public final NumberPath<Long> id;

    // inherited
    public final NumberPath<BigDecimal> percentageRateValue;

    // inherited
    public final EnumPath<TaxRateType> taxRateType;

    // inherited
    public final StringPath uid;

    // inherited
    public final DateTimePath<Date> updateTimestamp;

    // inherited
    public final DateTimePath<Date> validFrom;

    // inherited
    public final DateTimePath<Date> validTo;

    // inherited
    public final NumberPath<BigDecimal> value;

    public QJPAPTTaxEntity(String variable) {
        this(com.premiumminds.billy.portugal.persistence.entities.jpa.JPAPTTaxEntity.class, forVariable(variable), INITS);
    }

    public QJPAPTTaxEntity(Path<? extends JPAPTTaxEntity> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QJPAPTTaxEntity(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QJPAPTTaxEntity(PathMetadata metadata, PathInits inits) {
        this(com.premiumminds.billy.portugal.persistence.entities.jpa.JPAPTTaxEntity.class, metadata, inits);
    }

    public QJPAPTTaxEntity(Class<? extends JPAPTTaxEntity> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this._super = new QJPATaxEntity(type, metadata, inits);
        this.active = _super.active;
        this.code = _super.code;
        this.createTimestamp = _super.createTimestamp;
        this.currency = _super.currency;
        this.description = _super.description;
        this.designation = _super.designation;
        this.entityVersion = _super.entityVersion;
        this.flatRateAmount = _super.flatRateAmount;
        this.id = _super.id;
        this.percentageRateValue = _super.percentageRateValue;
        this.taxRateType = _super.taxRateType;
        this.uid = _super.uid;
        this.updateTimestamp = _super.updateTimestamp;
        this.validFrom = _super.validFrom;
        this.validTo = _super.validTo;
        this.value = _super.value;
    }
}
