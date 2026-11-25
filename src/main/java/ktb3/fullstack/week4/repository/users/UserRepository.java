package ktb3.fullstack.week4.repository.users;

import ktb3.fullstack.week4.domain.images.ProfileImage;
import ktb3.fullstack.week4.domain.users.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findByEmail(String email);

    // 네이티브 쿼리라 @SQLRestriction이 붙지 않아 탈퇴 회원까지 본다 (DB UNIQUE와 같은 범위)
    @Query(value = "SELECT CASE WHEN EXISTS(SELECT 1 FROM users WHERE email = :email) THEN 'true' ELSE 'false' END", nativeQuery = true)
    boolean existsByEmail(@Param("email") String email);

    @Query(value = "SELECT CASE WHEN EXISTS(SELECT 1 FROM users WHERE nickname = :nickname) THEN 'true' ELSE 'false' END", nativeQuery = true)
    boolean existsByNickname(@Param("nickname") String nickname);

    void deleteById(Long id);

    // 대표 변경은 이 쿼리로만 한다 (User#primaryProfileImage는 updatable = false). 포인터 컬럼만 갱신해 동시에 바뀐 닉네임 등을 덮어쓰지 않는다
    @Modifying
    @Query("update User u set u.primaryProfileImage = :image where u.id = :userId")
    int updatePrimaryProfileImage(@Param("userId") long userId, @Param("image") ProfileImage image);
}