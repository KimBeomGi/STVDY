import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore, useAlertStore, useUserStore } from '@/stores'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      alias: ['/home'],
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
    },
    {
      path: '/mypage',
      component: () => import('@/views/MyPageView.vue'),
      children: [
        {
          path: '',
          component: () => import('@/components/profile/MyPage.vue'),
          name: 'mypage',
          children: [
            {
              path: 'changepwd',
              component: () => import('@/components/profile/ChangePwd.vue'),
            },
            {
              path: 'changecolor',
              component: () => import('@/components/profile/ChangeColor.vue'),
            },
          ],
        },
        {
          path: '/alarms',
          name: 'alarms',
          component: () => import('@/components/alarm/MyAlarmList.vue'),
        },
      ],
    },
    {
      path: '/question',
      component: () => import('@/views/QuestionView.vue'),
      children: [
        {
          path: '/question',
          name: 'questionList',
          component: () => import('@/components/qna/QuestionList.vue'),
        },
        {
          path: '/myquestion',
          name: 'myQuestion',
          component: () => import('@/components/qna/MyQuestionList.vue'),
        },
        {
          path: '/createquestion',
          name: 'createQuestion',
          component: () => import('@/components/qna/CreateQuestion.vue'),
        },
        {
          path: '/modifyquestion/:id',
          name: 'modifyQuestion',
          component: () => import('@/components/qna/CreateQuestion.vue'),
        },
        {
          path: '/questiondetail/:id',
          name: 'questionDetail',
          params: {},
          component: () => import('@/components/qna/QuestionDetail.vue'),
        },
      ],
    },
    {
      path: '/about',
      // route level code-splitting
      // this generates a separate chunk (About.[hash].js) for this route
      // which is lazy-loaded when the route is visited.
      component: () => import('@/views/AboutView.vue'),
      children: [
        {
          path: '',
          name: 'login',
          component: () => import('@/components/ent/LoginBox.vue'),
        },
        {
          path: '/regist',
          name: 'regist',
          component: () => import('@/components/ent/RegisterBox.vue'),
        },
        {
          path: '/passwordReset',
          name: 'passwordReset',
          component: () => import('@/components/ent/PasswordReset.vue'),
        },
      ],
    },
    {
      path: '/room',
      component: () => import('@/views/RoomView.vue'),
      children: [
        {
          path: '',
          name: 'roomList',
          component: () => import('@/components/webrtc/RoomList.vue'),
        },
        {
          path: '/room/search/:keyword',
          name: 'listkeywordroom',
          component: () => import('@/components/webrtc/RoomSearchlist.vue'),
        },
      ],
    },
    {
      // 화상 채팅 방 참여
      path: '/room/:roomName',
      name: 'roomJoin',
      component: () => import('@/components/webrtc/RoomJoin.vue'),
    },

    //// 메인화면입장 시
    // {
    //   path: '/room',
    //   name: 'maintmp',
    //   component: () => import('@/components/webrtc/RoomList.vue'),
    //   children: [],
    // },
    {
      // path: '/'+{keyword},
      path: '/keyword/:TagName',
      name: 'KeywordRoom',
      component: () => import('@/components/main/KeywordRoom.vue'),
    },
  ],
})

router.beforeEach(async (to) => {
  // 컬러 팔레트를 로컬 스토리지에서 찾는다.
  const obj = localStorage.getItem('colorScheme')

  // 컬러 팔레트에 설정 된 값만 변경하고 나머지는 그대로 둔다.
  if (obj) {
    const colorScheme = JSON.parse(obj)
    for (const color in colorScheme) {
      document.documentElement.style.setProperty(
        '--' + color.replace(/([a-z])([A-Z])/g, '$1-$2').toLowerCase(),
        colorScheme[color],
      )
    }
  }

  // 유저 스토어 접근 후 경고를 알리는 스토어
  const alertStore = useAlertStore()
  alertStore.clear()

  // 로그인 없이도 접근 가능한 라우터
  // 접근 하려는 라우터가 public 인지 확인
  const loginLogics = ['login', 'regist', 'passwordReset']
  const anyLogics = [
    'login',
    'regist',
    'passwordReset',
    'home',
    'questionList',
    'questionDetail',
    'roomList',
  ]

  // 로컬 스토리지의 유저 로그인 정보가 있는지 받아오는 스토어
  const authStore = useAuthStore()
  const userStore = useUserStore()

  let token = localStorage.getItem('access-token')
  if (typeof token == 'undefined' || token == null || token == '')
    token = sessionStorage.getItem('access-token')

  if (!(typeof token == 'undefined' || token == null || token == ''))
    await userStore.getInfo(token)

  // public 라우터이면 유저 정보를 확인하지 않아 무한 반복 방지
  // 없으면 끝도 없이 유저 정보 검사후 이동을 반복
  if (authStore.isLogin) {
    if (loginLogics.includes(to.name)) {
      authStore.returnUrl = to.fullPath

      return '/'
    }
  } else {
    if (!anyLogics.includes(to.name)) {
      authStore.returnUrl = to.fullPath

      return '/about'
    }
  }
})

export default router
