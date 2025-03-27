uniform mat4 textureTransform; //类似Matrix 矩阵 uniform 可共享的全局变量
attribute vec2 inputTextureCoordinate;   //attribute只能在顶点GLSL中使用 用于表示某个变量
attribute vec4 position;            //NDK坐标点
varying   vec2 textureCoordinate; //纹理坐标点变换后输出  一般用于顶点Shader 和 纹理Shader 之间做数据交流

 void main() {
     gl_Position = position;
     textureCoordinate = inputTextureCoordinate;
 }