package com.a505.hobbyit.member.service;

import com.a505.hobbyit.common.file.FileUploader;
import com.a505.hobbyit.hobby.domain.Hobby;
import com.a505.hobbyit.hobby.domain.HobbyRepository;
import com.a505.hobbyit.hobby.exception.NoSuchHobbyException;
import com.a505.hobbyit.hobbymember.domain.HobbyMember;
import com.a505.hobbyit.member.domain.Member;
import com.a505.hobbyit.member.dto.request.*;
import com.a505.hobbyit.member.dto.response.MemberHobbyResponse;
import com.a505.hobbyit.member.dto.response.MemberPendingResponse;
import com.a505.hobbyit.member.dto.response.MemberResponse;
import com.a505.hobbyit.member.dto.response.MypageResponse;
import com.a505.hobbyit.member.enums.MemberIsSns;
import com.a505.hobbyit.member.enums.MemberPrivilege;
import com.a505.hobbyit.jwt.JwtTokenProvider;
import com.a505.hobbyit.member.enums.MemberState;
import com.a505.hobbyit.member.exception.*;
import com.a505.hobbyit.member.domain.MemberRepository;
import com.a505.hobbyit.pending.domain.Pending;
import com.a505.hobbyit.security.SecurityUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
@Service
public class MemberServiceImpl implements MemberService {
    @Value("${oauth.REST_API_KEY}")
    private String REST_API_KEY;
    @Value("${oauth.redirect_uri}")
    private String redirect_uri;

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final StringRedisTemplate stringRedisTemplate;
    private final JavaMailSender javaMailSender;
    private final HobbyRepository hobbyRepository;
    private final SecurityUtil securityUtil;
    private final FileUploader fileUploader;

    @Override
    public void signUp(MemberSignupRequest request) {
        if (memberRepository.existsByEmail(request.getEmail())) {
            throw new DuplicatedMemberException();
        }

        if (memberRepository.existsByNickname(request.getNickname())) {
            throw new DuplicatedMemberException("????????? ??????????????????.");
        }

        Member member = Member.builder()
                .email(request.getEmail())
                .name(request.getName())
                .nickname(request.getNickname())
                .password(passwordEncoder.encode(request.getPassword()))
                .isSns(MemberIsSns.FALSE)
                .state(MemberState.ACTIVE)
                .privilege(Collections.singleton(MemberPrivilege.GENERAL.name()))
                .build();
        memberRepository.save(member);
    }

    @Override
    public boolean isSns(String email) {
        return memberRepository.existsByEmailAndIsSns(email, MemberIsSns.TRUE);
    }

    @Transactional
    @Override
    public void updateSnsMember(String email, String imgUrl) {
        Member member = memberRepository.findByEmail(email).orElseThrow(NoSuchMemberException::new);
        member.updateSnsMember(imgUrl);
    }

    @Transactional
    @Override
    public MemberResponse login(MemberLoginRequest request) {
        Member member = memberRepository.findByEmail(request.getEmail()).orElseThrow(NoSuchElementException::new);

        // 0. ????????? ????????? ????????????.
        MemberState state = member.getState();
        if (state.equals(MemberState.BAN)) {
            throw new NoSuchMemberException("????????? ????????? ?????? ???????????????.");
        } else if (state.equals(MemberState.RESIGNED)) {
            throw new NoSuchMemberException("????????? ???????????????.");
        } else if (state.equals(MemberState.WAITING)) {
            member.updateState(MemberState.ACTIVE, null);
        }

        // 1. Login ID/PW ??? ???????????? Authentication ?????? ??????
        // ?????? authentication ??? ?????? ????????? ???????????? authenticated ?????? false
        UsernamePasswordAuthenticationToken authenticationToken = request.toAuthentication();

        // 2. ?????? ?????? (????????? ???????????? ??????)??? ??????????????? ??????
        // authenticate ???????????? ????????? ??? CustomUserDetailsService ?????? ?????? loadUserByUsername ???????????? ??????
        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);

        // 3. ?????? ????????? ???????????? JWT ?????? ??????
        MemberResponse tokenInfo = jwtTokenProvider.generateToken(authentication, member, true, "");

        // 4. RefreshToken Redis ?????? (expirationTime ????????? ?????? ?????? ?????? ??????)
        stringRedisTemplate.opsForValue()
                .set("RT:" + member.getId(), tokenInfo.getRefreshToken(), tokenInfo.getRefreshTokenExpirationTime(), TimeUnit.MILLISECONDS);
        return tokenInfo;
    }

    @Override
    public MemberResponse reissue(MemberReissueRequest request) {
        // 1. Refresh Token ??????
        if (!jwtTokenProvider.validateToken(request.getRefreshToken())) {
            throw new InvalidedRefreshTokenException();
        }

        // 2. Refresh Token ?????? Member id ??? ???????????????.
        Authentication authentication = jwtTokenProvider.getAuthentication(request.getRefreshToken());

        Member member = memberRepository.findById(Long.parseLong(authentication.getName())).orElseThrow(NoSuchElementException::new);

        // 3. Redis ?????? Member id ??? ???????????? ????????? Refresh Token ?????? ???????????????.
        String refreshToken = stringRedisTemplate.opsForValue().get("RT:" + member.getId());

        // ?????????????????? Redis ??? RefreshToken ??? ???????????? ?????? ?????? ??????
        if (ObjectUtils.isEmpty(refreshToken)) {
            throw new InvalidedRefreshTokenException("???????????? ???????????????.");
        }
        if (!refreshToken.equals(request.getRefreshToken())) {
            throw new InvalidedRefreshTokenException("Refresh Token ????????? ???????????? ????????????.");
        }

        // 4. ????????? ?????? ??????
        MemberResponse tokenInfo = jwtTokenProvider.generateToken(authentication, member, false, request.getRefreshToken());

        // 5. RefreshToken Redis ????????????
        stringRedisTemplate.opsForValue()
                .set("RT:" + authentication.getName(), tokenInfo.getRefreshToken(), tokenInfo.getRefreshTokenExpirationTime(), TimeUnit.MILLISECONDS);

        return tokenInfo;
    }

    @Override
    public void logout(final String token) {
        String accessToken = token.split(" ")[1];

        // 1. Access Token ??????
        if (!jwtTokenProvider.validateToken(accessToken)) {
            throw new InvalidedAccessTokenException();
        }

        // 2. Access Token ?????? Member email ??? ???????????????.
        Authentication authentication = jwtTokenProvider.getAuthentication(accessToken);

        // 3. Redis ?????? ?????? Member email ??? ????????? Refresh Token ??? ????????? ????????? ?????? ??? ?????? ?????? ???????????????.
        if (stringRedisTemplate.opsForValue().get("RT:" + authentication.getName()) != null) {
            // Refresh Token ??????
            stringRedisTemplate.delete("RT:" + authentication.getName());
        }

        // 4. ?????? Access Token ???????????? ????????? ?????? BlackList ??? ????????????
        Long expiration = jwtTokenProvider.getExpiration(token);
        stringRedisTemplate.opsForValue().set(accessToken, "logout", expiration, TimeUnit.MILLISECONDS);
    }

    @Transactional
    @Override
    public void resetPassword(MemberMailRequest request, String from) throws MessagingException {
        // 1. ????????? ???????????? ????????? ????????? ???????????? ???????????? ??????????????? ??????
        Member member = memberRepository.findByEmailAndName(request.getEmail(), request.getName()).orElseThrow(NoSuchMemberException::new);

        // 2. ???????????? ??????????????? ???????????? ???????????? ?????? ??????
        char[] charSet = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F',
                'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'};

        String pwd = "";
        for (int i = 0, idx = 0; i < 10; i++) {
            idx = (int) (charSet.length * Math.random());
            pwd += charSet[idx];
        }

        member.resetPassword(passwordEncoder.encode(pwd));

        // 3. ???????????? ?????? ?????? ????????? ???????????? ??????
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        // true??? ???????????? ???????????? ?????????????????? ??????
        MimeMessageHelper mimeMessageHelper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        mimeMessageHelper.setFrom(from);
        mimeMessageHelper.setTo(request.getEmail());
        mimeMessageHelper.setSubject("HOBBY'IT ?????????????????? ?????? ????????? ?????????.");
        // true??? html??? ?????????????????? ??????
        mimeMessageHelper.setText("???????????????. HOBBY'IT ?????????????????? ?????? ?????? ????????? ?????????.<br> " + " ???????????? ?????? ??????????????? <b>"
                + pwd + "</b> ?????????.<br> " + "????????? ?????? ??????????????? ????????? ????????????.", true);
//        mimeMessageHelper.addInline("logo", new FileDataSource("C:/Users/KANG/Desktop/logo.png"));
        javaMailSender.send(mimeMessage);
    }

    @Override
    public MypageResponse findMypage(final String token, final String nickname) {
        String id = jwtTokenProvider.getUser(token);
        memberRepository.findById(Long.parseLong(id)).orElseThrow(NoSuchMemberException::new);

        Member member = memberRepository.findByNickname(nickname)
                .orElseThrow(NoSuchMemberException::new);

        MypageResponse mypageResponse;
        if (memberRepository.existsByIdAndNickname(id, nickname)) {
            mypageResponse = MypageResponse.builder()
                    .email(member.getEmail())
                    .name(member.getName())
                    .nickname(member.getNickname())
                    .intro(member.getIntro())
                    .point(member.getPoint())
                    .pointLevel(member.getPoint() / 100)
                    .pointExp(member.getPoint() % 100)
                    .imgUrl(member.getImgUrl())
                    .build();
        } else {
            mypageResponse = MypageResponse.builder()
                    .nickname(member.getNickname())
                    .intro(member.getIntro())
                    .point(member.getPoint())
                    .pointLevel(member.getPoint() / 100)
                    .pointExp(member.getPoint() % 100)
                    .imgUrl(member.getImgUrl())
                    .build();
        }

        return mypageResponse;
    }

    @Transactional
    @Override
    public void update(final String token, MemberMypageRequest request, MultipartFile multipartFile) {
        Member member = memberRepository.findById(Long.parseLong(jwtTokenProvider.getUser(token)))
                .orElseThrow(NoSuchMemberException::new);

        String imgUrl = fileUploader.upload(multipartFile, "member");

        // ????????? ?????? ??????
        if ("".equals(request.getNickname())) {
            throw new NoSuchMemberException("???????????? ?????? ?????? ???????????????.");
        }

        // ????????? ????????????
        if (!member.getNickname().equals(request.getNickname()) &&
                memberRepository.existsByNickname(request.getNickname())) {
            throw new DuplicatedMemberException("????????? ??????????????????.");
        }

        member.updateMember(request, imgUrl);
        if (!"".equals(request.getPassword())) {
            member.resetPassword(passwordEncoder.encode(request.getPassword()));
        }

        // sns????????? ???????????? ???????????? ????????? ?????????.
        if (member.getIsSns().equals(MemberIsSns.TRUE)) {
            member.resetPassword(passwordEncoder.encode(member.getEmail()));
        }
    }

    @Transactional
    @Override
    public void delete(final String token) {
        Member member = memberRepository.findById(Long.parseLong(jwtTokenProvider.getUser(token)))
                .orElseThrow(NoSuchMemberException::new);
        member.checkWaiting();
        member.updateState(MemberState.WAITING, LocalDateTime.now());
    }

    @Override
    public List<MemberHobbyResponse> getHobbyList(final String token, String nickname) {
        String id = jwtTokenProvider.getUser(token);
        memberRepository.findById(Long.parseLong(id)).orElseThrow(NoSuchMemberException::new);

        Member member = memberRepository.findByNickname(nickname)
                .orElseThrow(NoSuchMemberException::new);

        List<HobbyMember> hobbyMembers = member.getHobbyMembers();

        List<MemberHobbyResponse> responses = new ArrayList<>();
        for (HobbyMember hobbyMember : hobbyMembers) {
            Hobby hobby = hobbyRepository.findById(hobbyMember.getHobby().getId()).orElseThrow(NoSuchHobbyException::new);
            MemberHobbyResponse response = new MemberHobbyResponse().of(hobby, hobbyMember);
            responses.add(response);
        }

        return responses;
    }

    @Override
    public List<MemberPendingResponse> getPendingList(final String token) {
        String id = jwtTokenProvider.getUser(token);
        Member member = memberRepository.findById(Long.parseLong(id)).orElseThrow(NoSuchMemberException::new);

        List<Pending> pendings = member.getPendings();

        List<MemberPendingResponse> responses = new ArrayList<>();
        for (Pending pending : pendings) {
            Hobby hobby = hobbyRepository.findById(pending.getHobby().getId()).orElseThrow(NoSuchHobbyException::new);
            MemberPendingResponse response = new MemberPendingResponse().of(hobby, pending);
            responses.add(response);
        }

        return responses;
    }

    @Override
    public String redirectKakao() {
        return "https://kauth.kakao.com/oauth/authorize?client_id=" + REST_API_KEY +
                "&redirect_uri=" + redirect_uri + "&response_type=code";
    }

    @Override
    public String getKakaoToken(String code) {
        String reqURL = "https://kauth.kakao.com/oauth/token";
        String oauth_token = "";

        try {
            URL url = new URL(reqURL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            //POST ????????? ?????? ???????????? false??? setDoOutput??? true???
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);

            //POST ????????? ????????? ???????????? ???????????? ???????????? ?????? ??????
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(conn.getOutputStream()));
            StringBuilder sb = new StringBuilder();
            sb.append("grant_type=authorization_code");
            sb.append("&client_id=" + REST_API_KEY);
            sb.append("&redirect_uri=" + redirect_uri);
            sb.append("&code=" + code);
            bw.write(sb.toString());
            bw.flush();

            //????????? ?????? ?????? JSON????????? Response ????????? ????????????
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line = "";
            String result = "";

            while ((line = br.readLine()) != null) {
                result += line;
            }

            //Gson ?????????????????? ????????? ???????????? JSON?????? ?????? ??????
            JsonParser parser = new JsonParser();
            JsonElement element = parser.parse(result);

            oauth_token = element.getAsJsonObject().get("access_token").getAsString();

            br.close();
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return oauth_token;
    }

    @Override
    public List<String> getKakaoMember(String token) {
        List<String> account = new ArrayList<>();

        String reqURL = "https://kapi.kakao.com/v2/user/me";

        // token??? ???????????? ????????? ?????? ??????
        try {
            URL url = new URL(reqURL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "Bearer " + token); //????????? header ??????, token??????

            //????????? ?????? ?????? JSON????????? Response ????????? ????????????
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line = "";
            String result = "";

            while ((line = br.readLine()) != null) {
                result += line;
            }

            //Gson ?????????????????? JSON??????
            JsonParser parser = new JsonParser();
            JsonElement element = parser.parse(result);

            account.add(element.getAsJsonObject().get("kakao_account").getAsJsonObject().get("email").getAsString());
            account.add(element.getAsJsonObject().get("properties").getAsJsonObject().get("nickname").getAsString());
            account.add(element.getAsJsonObject().get("properties").getAsJsonObject().get("profile_image").getAsString());

            br.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        return account;
    }

}
