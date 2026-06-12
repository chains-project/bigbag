package com.premiumminds.billy.portugal.persistence.entities.jpa;
import EntityPathBase;
import JPAPTSupplierEntity;
import com.premiumminds.billy.persistence.entities.jpa.JPAAddressEntity;
import com.premiumminds.billy.persistence.entities.jpa.JPABankAccountEntity;
import com.premiumminds.billy.persistence.entities.jpa.JPAContactEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPAAddressEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPABankAccountEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPAContactEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPASupplierEntity;
import java.util.Date;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.types.dsl.*;
import com.querydsl.core.types.dsl.PathInits;
import static com.querydsl.core.types.PathMetadataFactory.*;
/**
 * QJPAPTSupplierEntity is a Querydsl query type for JPAPTSupplierEntity
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QJPAPTSupplierEntity extends EntityPathBase<JPAPTSupplierEntity> {
    private static final long serialVersionUID = -1853496390L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QJPAPTSupplierEntity jPAPTSupplierEntity = new QJPAPTSupplierEntity("jPAPTSupplierEntity");

    public final QJPASupplierEntity _super;

    // inherited
    public final BooleanPath active;

    // inherited
    public final ListPath<JPAAddressEntity, QJPAAddressEntity> addresses;

    // inherited
    public final ListPath<JPABankAccountEntity, QJPABankAccountEntity> bankAccounts;

    // inherited
    public final QJPAAddressEntity billingAddress;

    // inherited
    public final ListPath<JPAContactEntity, QJPAContactEntity> contacts;

    // inherited
    public final DateTimePath<Date> createTimestamp;

    // inherited
    public final NumberPath<Integer> entityVersion;

    // inherited
    public final NumberPath<Long> id;

    // inherited
    public final QJPAAddressEntity mainAddress;

    // inherited
    public final QJPAContactEntity mainContact;

    // inherited
    public final StringPath name;

    public final StringPath referralName = createString("referralName");

    // inherited
    public final BooleanPath selfBillingAgreement;

    // inherited
    public final QJPAAddressEntity shippingAddress;

    // inherited
    public final StringPath taxRegistrationNumber;

    // inherited
    public final StringPath taxRegistrationNumberISOCountryCode;

    // inherited
    public final StringPath uid;

    // inherited
    public final DateTimePath<Date> updateTimestamp;

    public QJPAPTSupplierEntity(String variable) {
        this(com.premiumminds.billy.portugal.persistence.entities.jpa.JPAPTSupplierEntity.class, forVariable(variable), INITS);
    }

    public QJPAPTSupplierEntity(Path<? extends JPAPTSupplierEntity> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QJPAPTSupplierEntity(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QJPAPTSupplierEntity(PathMetadata metadata, PathInits inits) {
        this(com.premiumminds.billy.portugal.persistence.entities.jpa.JPAPTSupplierEntity.class, metadata, inits);
    }

    public QJPAPTSupplierEntity(Class<? extends JPAPTSupplierEntity> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this._super = new QJPASupplierEntity(type, metadata, inits);
        this.active = _super.active;
        this.addresses = _super.addresses;
        this.bankAccounts = _super.bankAccounts;
        this.billingAddress = _super.billingAddress;
        this.contacts = _super.contacts;
        this.createTimestamp = _super.createTimestamp;
        this.entityVersion = _super.entityVersion;
        this.id = _super.id;
        this.mainAddress = _super.mainAddress;
        this.mainContact = _super.mainContact;
        this.name = _super.name;
        this.selfBillingAgreement = _super.selfBillingAgreement;
        this.shippingAddress = _super.shippingAddress;
        this.taxRegistrationNumber = _super.taxRegistrationNumber;
        this.taxRegistrationNumberISOCountryCode = _super.taxRegistrationNumberISOCountryCode;
        this.uid = _super.uid;
        this.updateTimestamp = _super.updateTimestamp;
    }
}
