package com.premiumminds.billy.portugal.persistence.entities.jpa;
import EntityPathBase;
import JPAPTCustomerEntity;
import com.premiumminds.billy.persistence.entities.jpa.JPAAddressEntity;
import com.premiumminds.billy.persistence.entities.jpa.JPABankAccountEntity;
import com.premiumminds.billy.persistence.entities.jpa.JPAContactEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPAAddressEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPABankAccountEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPAContactEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPACustomerEntity;
import java.util.Date;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.types.dsl.*;
import com.querydsl.core.types.dsl.PathInits;
import static com.querydsl.core.types.PathMetadataFactory.*;
/**
 * QJPAPTCustomerEntity is a Querydsl query type for JPAPTCustomerEntity
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QJPAPTCustomerEntity extends EntityPathBase<JPAPTCustomerEntity> {
    private static final long serialVersionUID = 205460556L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QJPAPTCustomerEntity jPAPTCustomerEntity = new QJPAPTCustomerEntity("jPAPTCustomerEntity");

    public final QJPACustomerEntity _super;

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
    public final BooleanPath selfBilling;

    // inherited
    public final QJPAAddressEntity shippingAddress;

    // inherited
    public final StringPath taxId;

    // inherited
    public final StringPath taxIdISOCountryCode;

    // inherited
    public final StringPath uid;

    // inherited
    public final DateTimePath<Date> updateTimestamp;

    public QJPAPTCustomerEntity(String variable) {
        this(com.premiumminds.billy.portugal.persistence.entities.jpa.JPAPTCustomerEntity.class, forVariable(variable), INITS);
    }

    public QJPAPTCustomerEntity(Path<? extends JPAPTCustomerEntity> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QJPAPTCustomerEntity(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QJPAPTCustomerEntity(PathMetadata metadata, PathInits inits) {
        this(com.premiumminds.billy.portugal.persistence.entities.jpa.JPAPTCustomerEntity.class, metadata, inits);
    }

    public QJPAPTCustomerEntity(Class<? extends JPAPTCustomerEntity> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this._super = new QJPACustomerEntity(type, metadata, inits);
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
        this.selfBilling = _super.selfBilling;
        this.shippingAddress = _super.shippingAddress;
        this.taxId = _super.taxId;
        this.taxIdISOCountryCode = _super.taxIdISOCountryCode;
        this.uid = _super.uid;
        this.updateTimestamp = _super.updateTimestamp;
    }
}
