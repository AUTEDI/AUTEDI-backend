package com.ceos24.autedi.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.LastModifiedDate;

@Getter
@MappedSuperclass
public abstract class BaseTimeEntity extends BaseCreatedTimeEntity {

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 자식 편집도 부모 리소스의 변경으로 기록한다. 버전 증가는 JPA에 맡긴다. */
    protected void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
