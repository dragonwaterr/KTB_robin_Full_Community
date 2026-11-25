package ktb3.fullstack.week4.domain.users;

import jakarta.persistence.*;
import ktb3.fullstack.week4.domain.SoftDeletetionEntity;
import ktb3.fullstack.week4.domain.images.ProfileImage;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

@Entity
@SuperBuilder
@Getter
@NoArgsConstructor
@Table(name = "users")
@SQLRestriction("deleted = false") // 모든 조회 쿼리에 자동으로 "where deleted = false" 추가
public class User extends SoftDeletetionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", unique = true)
    private String email;

    @Column(name = "hashed_password")
    private String hashedPassword;

    @Column(name = "nickname", unique = true)
    private String nickname;

    @Column(name = "role", columnDefinition = "VARCHAR(10)")
    private String role;

    @Builder.Default
    @SQLRestriction("deleted = false") // 모든 조회 쿼리에 자동으로 "where deleted = false" 추가
    @OneToMany(mappedBy = "user")
    private List<ProfileImage> profileImages = new ArrayList<>();

    @OneToOne(fetch = FetchType.LAZY)
    // 더티 체킹은 users 전 컬럼을 UPDATE하므로, 닉네임 변경 등이 그사이 바뀐 대표를 되돌리지 않게 UPDATE에서 뺀다
    @JoinColumn(name = "primary_profile_image_id", nullable = false, updatable = false)
    private ProfileImage primaryProfileImage;


    public void changeNickName(String newNickname) {
        nickname = newNickname;
    }

    public void changePassword(String newPassword) {
        hashedPassword = newPassword;
    }

    public void changePrimaryProfileImage(ProfileImage profileImage) {
        primaryProfileImage = profileImage;
    }
}
