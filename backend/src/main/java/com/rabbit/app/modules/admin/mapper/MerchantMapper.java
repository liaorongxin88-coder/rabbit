package com.rabbit.app.modules.admin.mapper;

import com.rabbit.app.modules.admin.entity.Merchant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MerchantMapper {
    int insert(Merchant merchant);

    Merchant selectById(@Param("id") Long id);

    List<Merchant> selectPage(@Param("keyword") String keyword, @Param("status") String status, @Param("offset") int offset, @Param("limit") int limit);

    long countPage(@Param("keyword") String keyword, @Param("status") String status);

    int updateBasic(@Param("id") Long id, @Param("name") String name, @Param("contactName") String contactName, @Param("contactPhone") String contactPhone, @Param("remark") String remark, @Param("updateBy") String updateBy);

    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("updateBy") String updateBy);

    int updateOwner(@Param("id") Long id,
                    @Param("ownerUserId") Long ownerUserId,
                    @Param("updateBy") String updateBy);
}
