<script setup>
import SideBar from '@/components/SideBar.vue'
import TopSearch from '@/components/TopSearch.vue'
import Advertisment from '@/components/home/Advertisment.vue'
import TagRank from '@/components/home/TagRank.vue'
import Words from '@/components/home/Words.vue'
import MeetingRoom from '@/components/home/MeetingRoom.vue'
import RecentAsk from '@/components/home/RecentAsk.vue'
import RoomAdd from '@/components/webrtc/RoomAdd.vue'

import { useUserStore } from '@/stores'
import { computed } from 'vue'

const userStore = useUserStore()
const user = computed(() => userStore.user)
</script>

<template>
  <SideBar />
  <TopSearch id="top-items" />
  <RoomAdd
    v-if="user.id"
    id="room-add" />
  <main id="home-main">
    <Advertisment />
    <TagRank />
    <Words />
    <MeetingRoom />
    <RecentAsk />
  </main>
</template>

<style>
#top-items {
  z-index: 100;
}

#room-add {
  z-index: 101;
}

#ai-assist {
  z-index: 102;
}

@media (min-width: 1600px) {
  #home-main {
    position: absolute;
    left: 270px;

    top: 70px;

    width: calc(100vw - 400px);
  }
}

@media (max-width: 1600px) {
  #home-main {
    position: absolute;
    left: calc(50vw - 480px);

    top: 70px;

    width: calc(960px);
  }
}

@media (max-width: 1360px) {
  #home-main {
    position: absolute;
    left: 200px;

    top: 70px;

    width: calc(960px);
  }
}
</style>
