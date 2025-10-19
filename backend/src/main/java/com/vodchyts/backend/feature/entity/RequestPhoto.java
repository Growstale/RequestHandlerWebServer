package com.vodchyts.backend.feature.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Setter
@Getter
@Table("RequestPhotos")
public class RequestPhoto {
    @Id
    @Column("RequestPhotoID")
    private Integer requestPhotoID;
    @Column("RequestID")
    private Integer requestID;
    @Column("ImageData")
    private byte[] imageData;

}
