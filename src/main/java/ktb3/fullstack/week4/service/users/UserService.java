package ktb3.fullstack.week4.service.users;

import ktb3.fullstack.week4.Security.service.AppPasswordEncoder;
import ktb3.fullstack.week4.domain.images.ProfileImage;
import ktb3.fullstack.week4.domain.users.User;
import ktb3.fullstack.week4.dto.users.*;
import ktb3.fullstack.week4.repository.images.ProfileImageRepository;
import ktb3.fullstack.week4.repository.users.UserRepository;
import ktb3.fullstack.week4.service.availabilities.AvailabilityService;
import ktb3.fullstack.week4.service.errors.ErrorCheckServiceImpl;
import ktb3.fullstack.week4.service.images.ImageDomainBuilder;
import ktb3.fullstack.week4.service.images.ProfileImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserDomainBuilder userDomainBuilder;
    private final ImageDomainBuilder imageDomainBuilder;

    private final AppPasswordEncoder passwordEncoder;

    private final ErrorCheckServiceImpl errorCheckService;
    private final AvailabilityService availabilityService;
    private final ProfileImageService profileImageService;

    private final UserRepository userRepository;
    private final ProfileImageRepository profileImageRepository;

    private final UserDeleteFacade userDeleteFacade;


    @Transactional
    public void register(JoinRequest dto, MultipartFile image) {
        availabilityService.checkRegisterAvailability(dto, image);

        String hashedPassword = passwordEncoder.encode(dto.getPassword());
        User user = userDomainBuilder.buildUser(hashedPassword, dto);

        String profileImageUrl = profileImageService.makeImagePathString(image);
        profileImageService.transferImageToLocalDirectory(image, profileImageUrl);

        String saved = profileImageUrl.split("static")[1];
        ProfileImage profileImage = imageDomainBuilder.buildProfileImage(user, saved);

        profileImageRepository.save(profileImage);
        user.changePrimaryProfileImage(profileImage);
        userRepository.save(user);
    }

    // 이메일, 닉네임 조회
    @Transactional(readOnly = true)
    public UserEditPageResponse getUserInfoForEditPage(long userId) {
        User user = errorCheckService.checkCanNotFoundUser(userId);
        return userDomainBuilder.buildUserPageResponse(user.getEmail(), user.getNickname());
    }

    // 닉네임 변경: 존재 확인 → 저장소 갱신
    @Transactional
    public NicknameUpdateResponse changeNickname(long userId, NicknameUpdateRequest dto) {
        User user = errorCheckService.checkCanNotFoundUser(userId);
        availabilityService.checkNewNicknameAvailability(dto);
        String newNickname = dto.getNewNickname();
        user.changeNickName(newNickname);
        return new NicknameUpdateResponse(newNickname);
    }


    // 비밀번호 변경
    @Transactional
    public void changePassword(long userId, PasswordUpdateRequest newPassword) {
        User user = errorCheckService.checkCanNotFoundUser(userId);
        String hashedPassword = passwordEncoder.encode(newPassword.getNewPassword());
        user.changePassword(hashedPassword);
    }

    // 프로필 이미지 변경: 존재 확인 후 저장(향후 Store/Repository 메서드 추가 시 연결)
    @Transactional
    public String changeProfileImage(long userId, MultipartFile newProfileImage) {
        User user = errorCheckService.checkCanNotFoundUser(userId);

        String profileImageUrl = profileImageService.makeImagePathString(newProfileImage);
        profileImageService.transferImageToLocalDirectory(newProfileImage, profileImageUrl);

        String saved = profileImageUrl.split("static")[1];
        ProfileImage profileImage = imageDomainBuilder.buildProfileImage(user, saved);

        List<ProfileImage> profileImages = profileImageRepository.findAllByUserId(userId);
        if (!profileImages.isEmpty()) {
            for (ProfileImage image : profileImages) {
                image.adjustDisplayOrder(); // displayOrder 순서 1씩 뒤로 밀기
            }
        }

        profileImageRepository.save(profileImage);
        userRepository.updatePrimaryProfileImage(userId, profileImage);
        return saved;
    }

    // 이후개발) 프로필 사진 history 에서 선택한 이미지만 삭제


    // 회원 탈퇴
    @Transactional
    public void withdrawMemberShip(long userId) {
        userDeleteFacade.deleteUser(userId); // 연관 데이터까지 모두 Soft Delete 적용하는 퍼사드
    }
}
