package com.vodchyts.backend.feature.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("RequestPhotos")
public class RequestPhoto {
    @Id
    @Column("RequestPhotoID")
    private Integer requestPhotoID;
    @Column("RequestID")
    private Integer requestID;
    @Column("ImageData")
    private byte[] imageData;

    // Геттеры и сеттеры
    public Integer getRequestPhotoID() { return requestPhotoID; }
    public void setRequestPhotoID(Integer requestPhotoID) { this.requestPhotoID = requestPhotoID; }
    public Integer getRequestID() { return requestID; }
    public void setRequestID(Integer requestID) { this.requestID = requestID; }
    public byte[] getImageData() { return imageData; }
    public void setImageData(byte[] imageData) { this.imageData = imageData; }
}
