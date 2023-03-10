import { createInstance, createInstanceWithNoAuth, fileConfig } from '@/api/index';

const PATH = '/member';

const instance = createInstance(PATH);
const multipartInstance = createInstance(PATH, fileConfig);
const instanceWithNoAuth = createInstanceWithNoAuth(PATH);
const multipartInstanceWithNoAuth = createInstanceWithNoAuth(PATH, fileConfig);

// 1.회원가입
function memberSignup(data) {
  return multipartInstanceWithNoAuth.post('/signup', data);
}

// 2.이메일인증

// 3.로그인
function memberLogin(data) {
  return multipartInstanceWithNoAuth.post('/login', data);
}

// 4.비밀번호 리셋
function resetPassword(data) {
  return multipartInstanceWithNoAuth.post('/password/reset', data);
}

// 5.로그아웃
// accessToken 같이 넘기기
function memberLogout() {
  return multipartInstance.get('/logout');
}

// 6.마이페이지(타인)
function getOthersMyPage(nickname) {
  return instance.get(`${nickname}`);
}

// 7.마이페이지 정보수정
function updateMyPage(data) {
  return multipartInstance.put('', data);
}

// 8.마이페이지 (본인)
function getMyPage() {
  return instance.get('');
}

// 9.회원탈퇴
function memberDelete() {
  return instance.put('/delete');
}

// 10.참여중인 모임 리스트 조회
function getParticipatingGroup(nickname) {
  return instance.get(`/hobby/${nickname}`);
}

// 11.대기중인 모임 리스트 조회
function getWaitingGroup() {
  return instance.get('/hobby/pending');
}

// 카카오로그인 요청
function getKakao() {
  return instanceWithNoAuth.get('/oauth/kakao/login');
}

export {
  memberSignup,
  memberLogin,
  getMyPage,
  memberLogout,
  getWaitingGroup,
  getParticipatingGroup,
  getOthersMyPage,
  updateMyPage,
  memberDelete,
  resetPassword,
  getKakao,
};
