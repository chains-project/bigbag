package com.premiumminds.billy.portugal.persistence.entities.jpa;
import EntityPathBase;
import JPAPTPaymentEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPAPaymentEntity;
import java.math.BigDecimal;
import java.util.Date;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.types.dsl.*;
import static com.querydsl.core.types.PathMetadataFactory.*;
/**
 * QJPAPTPaymentEntity is a Querydsl query type for JPAPTPaymentEntity
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QJPAPTPaymentEntity extends EntityPathBase<JPAPTPaymentEntity> {
    private static final long serialVersionUID = 12761406L;

    public static final QJPAPTPaymentEntity jPAPTPaymentEntity = new QJPAPTPaymentEntity("jPAPTPaymentEntity");

    public final QJPAPaymentEntity _super = new QJPAPaymentEntity(this);

    // inherited
    public final BooleanPath active = _super.active;

    // inherited
    public final DateTimePath<Date> createTimestamp = _super.createTimestamp;

    // inherited
    public final NumberPath<Integer> entityVersion = _super.entityVersion;

    // inherited
    public final NumberPath<Long> id = _super.id;

    public final NumberPath<BigDecimal> paymentAmount = createNumber("paymentAmount", BigDecimal.class);

    // inherited
    public final DateTimePath<Date> paymentDate = _super.paymentDate;

    // inherited
    public final StringPath paymentMethod = _super.paymentMethod;

    // inherited
    public final StringPath uid = _super.uid;

    // inherited
    public final DateTimePath<Date> updateTimestamp = _super.updateTimestamp;

    public QJPAPTPaymentEntity(String variable) {
        super(com.premiumminds.billy.portugal.persistence.entities.jpa.JPAPTPaymentEntity.class, forVariable(variable));
    }

    public QJPAPTPaymentEntity(Path<? extends JPAPTPaymentEntity> path) {
        super(path.getType(), path.getMetadata());
    }

    public QJPAPTPaymentEntity(PathMetadata metadata) {
        super(com.premiumminds.billy.portugal.persistence.entities.jpa.JPAPTPaymentEntity.class, metadata);
    }
}
