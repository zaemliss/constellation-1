package com.twitter.chill;

public class ProductEntity {

    public static final Class<ProductEntity> clazz = ProductEntity.class;

        public ProductEntity(String name) {
            this.name = name;
        }

    public void setName(String name) {
        this.name = name;
    }

    public ProductEntity() {
    }

    private String name;

        public String getName() {
            return name;
        }
    }