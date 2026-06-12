package com.premiumminds.billy.portugal.persistence.entities.jpa;
import EntityPathBase;
import JPAPTProductEntity;
import com.premiumminds.billy.core.services.entities.Product.ProductType;
import com.premiumminds.billy.persistence.entities.jpa.JPATaxEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPAProductEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPATaxEntity;
import java.util.Date;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.types.dsl.*;
import static com.querydsl.core.types.PathMetadataFactory.*;
/**
 * QJPAPTProductEntity is a Querydsl query type for JPAPTProductEntity
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QJPAPTProductEntity extends EntityPathBase<JPAPTProductEntity> {
    private static final long serialVersionUID = -375078553L;

    public static final QJPAPTProductEntity jPAPTProductEntity = new QJPAPTProductEntity("jPAPTProductEntity");

    public final QJPAProductEntity _super = new QJPAProductEntity(this);

    // inherited
    public final BooleanPath active = _super.active;

    // inherited
    public final StringPath commodityCode = _super.commodityCode;

    // inherited
    public final DateTimePath<Date> createTimestamp = _super.createTimestamp;

    // inherited
    public final StringPath description = _super.description;

    // inherited
    public final NumberPath<Integer> entityVersion = _super.entityVersion;

    // inherited
    public final StringPath group = _super.group;

    // inherited
    public final NumberPath<Long> id = _super.id;

    // inherited
    public final StringPath numberCode = _super.numberCode;

    // inherited
    public final StringPath productCode = _super.productCode;

    // inherited
    public final ListPath<JPATaxEntity, QJPATaxEntity> taxes = _super.taxes;

    // inherited
    public final EnumPath<ProductType> type = _super.type;

    // inherited
    public final StringPath uid = _super.uid;

    // inherited
    public final StringPath unitOfMeasure = _super.unitOfMeasure;

    // inherited
    public final DateTimePath<Date> updateTimestamp = _super.updateTimestamp;

    // inherited
    public final StringPath valuationMethod = _super.valuationMethod;

    public QJPAPTProductEntity(String variable) {
        super(com.premiumminds.billy.portugal.persistence.entities.jpa.JPAPTProductEntity.class, forVariable(variable));
    }

    public QJPAPTProductEntity(Path<? extends JPAPTProductEntity> path) {
        super(path.getType(), path.getMetadata());
    }

    public QJPAPTProductEntity(PathMetadata metadata) {
        super(com.premiumminds.billy.portugal.persistence.entities.jpa.JPAPTProductEntity.class, metadata);
    }
}
