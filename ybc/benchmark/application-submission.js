import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: __ENV.VUS ? Number(__ENV.VUS) : 20,
  duration: __ENV.DURATION || '20s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<2000'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'max'],
};

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const uniqueId = `${__VU}-${__ITER}-${Date.now()}`;
  const payload = JSON.stringify({
    name: `benchmark-user-${uniqueId}`,
    address: '서울특별시 서대문구',
    gender: 'MALE',
    age: 23,
    phone: '010-1234-5678',
    university: '이화여자대학교',
    major: '체육과학부',
    introduction: '배드민턴을 꾸준히 쳐 왔고 동아리 활동에도 적극적으로 참여하고 싶습니다.',
    motivation: '함께 배드민턴을 즐기며 좋은 사람들과 교류하고 싶어서 지원했습니다.',
    equipment: '배드민턴 라켓 2자루와 실내 운동화',
    interviewTimes: ['FIRST_SESSION', 'SECOND_SESSION'],
    discoverySource: 'INSTAGRAM',
    discoveryEtc: '인스타그램 모집 게시글',
    wantsStaff: true,
    finalComment: '열심히 참여하겠습니다.',
    status: 'NONE',
    email: `benchmark-${uniqueId}@example.com`,
  });

  const response = http.post(`${baseUrl}/applications`, payload, {
    headers: {
      'Content-Type': 'application/json',
    },
  });

  check(response, {
    'status is 201': (res) => res.status === 201,
  });

  sleep(1);
}
