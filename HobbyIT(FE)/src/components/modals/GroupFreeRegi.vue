<template>
  <v-card id="groupfreeregi">
    <v-icon icon="mdi-close" style="align-self: end" @click="close"></v-icon>
    <v-icon icon="mdi-party-popper" style="align-self: center"></v-icon>
    <div style="font-size: 36px">Welcome!</div>
    <div style="font-size: 14px; font-family: linefont">"{{ groupname }}" 모임에 가입이 완료되었습니다!</div>
    <div style="font-size: 20px; font-family: linefont">즐거운 시간 보내세요</div>

    <span id="okbtn" style="color: white" @click="close"> 확인 </span>
  </v-card>
</template>

<script>
export default {
  props : {
    groupname : String,
  },
  methods: {
    
    close() {
      this.$emit('close');
    },
    send(){
      this.$emit('send');
    }
  },
};
</script>

<style>
#groupfreeregi {
  width: 670px;
  height: 330px;
  display: flex;
  align-self: center;
  text-align: center;
  /*justify-content:center; */
  background-color: #0e0f28;
  color: white;
  font-family: linefontbold;
  border: 1px solid #24b1d0;
  border-radius: 20px;
  padding: 5px 20px;
  justify-content: space-evenly;
  align-items: center;
}

#okbtn {
  background: linear-gradient(to bottom, #ee49fd, #0e0f28);
  width: 60px;
  height: 60px;
  border-radius: 50%;
  display: flex;
  cursor: pointer;
  justify-content: center;
  align-items: center;
}
</style>
