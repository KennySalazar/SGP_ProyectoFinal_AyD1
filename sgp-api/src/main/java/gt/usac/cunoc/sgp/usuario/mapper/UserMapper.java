package gt.usac.cunoc.sgp.usuario.mapper;

import gt.usac.cunoc.sgp.usuario.dto.UserResponse;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {
  @Mapping(target = "role", expression = "java(user.getRole().getName().name())")
  UserResponse toResponse(UserAccount user);
}
