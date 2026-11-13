package com.premiumminds.billy.portugal.persistence.entities.jpa;
import EntityPathBase;
import JPAPTBusinessEntity;
import com.premiumminds.billy.persistence.entities.jpa.JPAApplicationEntity;
import com.premiumminds.billy.persistence.entities.jpa.JPAContactEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPAAddressEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPAApplicationEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPABusinessEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPAContactEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPAContextEntity;
import java.time.ZoneId;
import java.util.Date;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.types.dsl.*;
import com.querydsl.core.types.dsl.PathInits;
import static com.querydsl.core.types.PathMetadataFactory.*;
/**
 * QJPAPTBusinessEntity is a Querydsl query type for JPAPTBusinessEntity
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QJPAPTBusinessEntity extends EntityPathBase<JPAPTBusinessEntity> {
    private static final long serialVersionUID = 290939566L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QJPAPTBusinessEntity jPAPTBusinessEntity = new QJPAPTBusinessEntity("jPAPTBusinessEntity");

    public final QJPABusinessEntity _super;

    // inherited
    public final BooleanPath active;

    // inherited
    public final QJPAAddressEntity address;

    // inherited
    public final ListPath<JPAApplicationEntity, QJPAApplicationEntity> applications;

    // inherited
    public final QJPAAddressEntity billingAddress;

    // inherited
    public final StringPath commercialName;

    // inherited
    public final ListPath<JPAContactEntity, QJPAContactEntity> contacts;

    // inherited
    public final DateTimePath<Date> createTimestamp;

    // inherited
    public final NumberPath<Integer> entityVersion;

    // inherited
    public final NumberPath<Long> id;

    // inherited
    public final QJPAContactEntity mainContact;

    // inherited
    public final StringPath name;

    // inherited
    public final QJPAContextEntity operationalContext;

    // inherited
    public final QJPAAddressEntity shippingAddress;

    // inherited
    public final StringPath taxId;

    // inherited
    public final StringPath taxIdISOCountryCode;

    // inherited
    public final SimplePath<ZoneId> timezone;

    // inherited
    public final StringPath uid;

    // inherited
    public final DateTimePath<Date> updateTimestamp;

    // inherited
    public final StringPath website;

    public QJPAPTBusinessEntity(String variable) {
        this(com.premiumminds.billy.portugal.persistence.entities.jpa.JPAPTBusinessEntity.class, forVariable(variable), INITS);
    }

    public QJPAPTBusinessEntity(Path<? extends JPAPTBusinessEntity> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QJPAPTBusinessEntity(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QJPAPTBusinessEntity(PathMetadata metadata, PathInits inits) {
        this(com.premiumminds.billy.portugal.persistence.entities.jpa.JPAPTBusinessEntity.class, metadata, inits);
    }

    public QJPAPTBusinessEntity(Class<? extends JPAPTBusinessEntity> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this._super = new QJPABusinessEntity(type, metadata, inits);
        this.active = _super.active;
        this.address = _super.address;
        this.applications = _super.applications;
        this.billingAddress = _super.billingAddress;
        this.commercialName = _super.commercialName;
        this.contacts = _super.contacts;
        this.createTimestamp = _super.createTimestamp;
        this.entityVersion = _super.entityVersion;
        this.id = _super.id;
        this.mainContact = _super.mainContact;
        this.name = _super.name;
        this.operationalContext = _super.operationalContext;
        this.shippingAddress = _super.shippingAddress;
        this.taxId = _super.taxId;
        this.taxIdISOCountryCode = _super.taxIdISOCountryCode;
        this.timezone = _super.timezone;
        this.uid = _super.uid;
        this.updateTimestamp = _super.updateTimestamp;
        this.website = _super.website;
    }
}
