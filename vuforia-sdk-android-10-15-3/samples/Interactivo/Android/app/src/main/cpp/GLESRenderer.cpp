/*===============================================================================
Copyright (c) 2023 PTC Inc. and/or Its Subsidiary Companies. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other
countries.
===============================================================================*/
#include "GLESRenderer.h"
#include "stb_image.h"

#include "GLES2/gl2.h"

#include "GLESUtils.h"
#include "Shaders.h"

#include <MemoryStream.h>
#include <Models.h>

#include <android/asset_manager.h>

#include <thread>

#include <iostream>

#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <GLES2/gl2.h>

using namespace std;
using namespace std::this_thread;

int mTextureDataHandle0 = 0;

bool
GLESRenderer::init(AAssetManager* assetManager)
{
    // Setup for Video Background rendering
    mVbShaderProgramID = GLESUtils::createProgramFromBuffer(textureVertexShaderSrc, textureFragmentShaderSrc);
    mVbVertexPositionHandle = glGetAttribLocation(mVbShaderProgramID, "vertexPosition");
    mVbTextureCoordHandle = glGetAttribLocation(mVbShaderProgramID, "vertexTextureCoord");
    mVbMvpMatrixHandle = glGetUniformLocation(mVbShaderProgramID, "modelViewProjectionMatrix");
    mVbTexSampler2DHandle = glGetUniformLocation(mVbShaderProgramID, "texSampler2D");

    // Setup for augmentation rendering
    mUniformColorShaderProgramID = GLESUtils::createProgramFromBuffer(uniformColorVertexShaderSrc, uniformColorFragmentShaderSrc);
    mUniformColorVertexPositionHandle = glGetAttribLocation(mUniformColorShaderProgramID, "vertexPosition");
    mUniformColorMvpMatrixHandle = glGetUniformLocation(mUniformColorShaderProgramID, "modelViewProjectionMatrix");
    mUniformColorColorHandle = glGetUniformLocation(mUniformColorShaderProgramID, "uniformColor");

    // Setup for guide view rendering
    mTextureUniformColorShaderProgramID = GLESUtils::createProgramFromBuffer(textureColorVertexShaderSrc, textureColorFragmentShaderSrc);
    mTextureUniformColorVertexPositionHandle = glGetAttribLocation(mTextureUniformColorShaderProgramID, "vertexPosition");
    mTextureUniformColorTextureCoordHandle = glGetAttribLocation(mTextureUniformColorShaderProgramID, "vertexTextureCoord");
    mTextureUniformColorMvpMatrixHandle = glGetUniformLocation(mTextureUniformColorShaderProgramID, "modelViewProjectionMatrix");
    mTextureUniformColorTexSampler2DHandle = glGetUniformLocation(mTextureUniformColorShaderProgramID, "texSampler2D");
    mTextureUniformColorColorHandle = glGetUniformLocation(mTextureUniformColorShaderProgramID, "uniformColor");

    // Setup for axis rendering
    mVertexColorShaderProgramID = GLESUtils::createProgramFromBuffer(vertexColorVertexShaderSrc, vertexColorFragmentShaderSrc);
    mVertexColorVertexPositionHandle = glGetAttribLocation(mVertexColorShaderProgramID, "vertexPosition");
    mVertexColorColorHandle = glGetAttribLocation(mVertexColorShaderProgramID, "vertexColor");
    mVertexColorMvpMatrixHandle = glGetUniformLocation(mVertexColorShaderProgramID, "modelViewProjectionMatrix");

    mModelTargetGuideViewTextureUnit = -1;

    std::vector<char> data; // for reading model files

    // Load objeto model
    {
        if (!readAsset(assetManager, "Cubo.obj", data))
        {
            return false;
        }
        if (!loadObjModel(data, mObjeto1VertexCount, mObjeto1Vertices, mObjeto1TexCoords))
        {
            return false;
        }
        data.clear();
        mObjeto1TextureUnit = -1;

        if (!readAsset(assetManager, "Cohete.obj", data))
        {
            return false;
        }
        if (!loadObjModel(data, mObjeto2VertexCount, mObjeto2Vertices, mObjeto2TexCoords))
        {
            return false;
        }
        data.clear();
        mObjeto2TextureUnit = -2;
    }

    return true;
}



void
GLESRenderer::deinit()
{
    if (mModelTargetGuideViewTextureUnit != -1)
    {
        GLESUtils::destroyTexture(mModelTargetGuideViewTextureUnit);
        mModelTargetGuideViewTextureUnit = -1;
    }
    if (mObjeto1TextureUnit != -1)
    {
        GLESUtils::destroyTexture(mObjeto1TextureUnit);
        mObjeto1TextureUnit = -1;
    }

    if (mObjeto2TextureUnit != -1)
    {
        GLESUtils::destroyTexture(mObjeto2TextureUnit);
        mObjeto2TextureUnit = -1;
    }
}


void
GLESRenderer::setObjeto1Texture(int width, int height, unsigned char* bytes)
{
    createTexture(width, height, bytes, mObjeto1TextureUnit);
}

void
GLESRenderer::setObjeto2Texture(int width, int height, unsigned char* bytes)
{
    createTexture(width, height, bytes, mObjeto2TextureUnit);
}

///////////////////////////////////////////////


void
GLESRenderer::renderVideoBackground(const VuMatrix44F& projectionMatrix, const float* vertices, const float* textureCoordinates,
                                    const int numTriangles, const unsigned int* indices, int textureUnit)
{
    GLboolean depthTest = GL_FALSE;
    GLboolean cullTest = GL_FALSE;

    glGetBooleanv(GL_DEPTH_TEST, &depthTest);
    glGetBooleanv(GL_CULL_FACE, &cullTest);

    glDisable(GL_DEPTH_TEST);
    glDisable(GL_CULL_FACE);

    // Load the shader and upload the vertex/texcoord/index data
    glUseProgram(mVbShaderProgramID);
    glVertexAttribPointer(static_cast<GLuint>(mVbVertexPositionHandle), 3, GL_FLOAT, GL_FALSE, 0, vertices);
    glVertexAttribPointer(static_cast<GLuint>(mVbTextureCoordHandle), 2, GL_FLOAT, GL_FALSE, 0, textureCoordinates);

    glUniform1i(mVbTexSampler2DHandle, textureUnit);

    // Render the video background with the custom shader
    // First, we enable the vertex arrays
    glEnableVertexAttribArray(static_cast<GLuint>(mVbVertexPositionHandle));
    glEnableVertexAttribArray(static_cast<GLuint>(mVbTextureCoordHandle));

    // Pass the projection matrix to OpenGL
    glUniformMatrix4fv(mVbMvpMatrixHandle, 1, GL_FALSE, projectionMatrix.data);

    // Then, we issue the render call
    glDrawElements(GL_TRIANGLES, numTriangles * 3, GL_UNSIGNED_INT, indices);

    // Finally, we disable the vertex arrays
    glDisableVertexAttribArray(static_cast<GLuint>(mVbVertexPositionHandle));
    glDisableVertexAttribArray(static_cast<GLuint>(mVbTextureCoordHandle));

    if (depthTest)
        glEnable(GL_DEPTH_TEST);

    if (cullTest)
        glEnable(GL_CULL_FACE);

    GLESUtils::checkGlError("Render video background");
}


void
GLESRenderer::renderWorldOrigin(VuMatrix44F& projectionMatrix, VuMatrix44F& modelViewMatrix)
{
    VuVector3F axis10cmSize{ 0.1f, 0.1f, 0.1f };
    renderAxis(projectionMatrix, modelViewMatrix, axis10cmSize, 4.0f);
    VuVector4F cubeColor{ 0.8, 0.8, 0.8, 1.0 };
    renderCube(projectionMatrix, modelViewMatrix, 0.015f, cubeColor);
}


void
GLESRenderer::renderImageTarget(VuMatrix44F& projectionMatrix, VuMatrix44F& modelViewMatrix, VuMatrix44F& scaledModelViewMatrix)
{
    VuMatrix44F scaledModelViewProjectionMatrix = vuMatrix44FMultiplyMatrix(projectionMatrix, scaledModelViewMatrix);


    glEnable(GL_DEPTH_TEST);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    float stateLineWidth;
    glGetFloatv(GL_LINE_WIDTH, &stateLineWidth);

    glUseProgram(mUniformColorShaderProgramID);

    glVertexAttribPointer(mUniformColorVertexPositionHandle, 3, GL_FLOAT, GL_TRUE, 0, (const GLvoid*)&squareVertices[0]);

    glEnableVertexAttribArray(mUniformColorVertexPositionHandle);

    glUniformMatrix4fv(mUniformColorMvpMatrixHandle, 1, GL_FALSE, &scaledModelViewProjectionMatrix.data[0]);

    // Draw translucent solid overlay
    // Color RGBA
    glUniform4f(mUniformColorColorHandle, 1.0, 0.0, 0.0, 0.1);
    glDrawElements(GL_TRIANGLES, NUM_SQUARE_INDEX, GL_UNSIGNED_SHORT, (const GLvoid*)&squareIndices[0]);

    // Draw solid outline
    glUniform4f(mUniformColorColorHandle, 1.0, 0.0, 0.0, 1.0);
    glLineWidth(4.0f);
    glDrawElements(GL_LINES, NUM_SQUARE_WIREFRAME_INDEX, GL_UNSIGNED_SHORT, (const GLvoid*)&squareWireframeIndices[0]);

    glDisableVertexAttribArray(mUniformColorVertexPositionHandle);

    GLESUtils::checkGlError("Render Image Target");

    glLineWidth(stateLineWidth);

    glDisable(GL_BLEND);
    glDisable(GL_DEPTH_TEST);

    VuVector3F axis2cmSize{ 0.02f, 0.02f, 0.02f };
    renderAxis(projectionMatrix, modelViewMatrix, axis2cmSize, 4.0f);

    VuMatrix44F modelViewProjectionMatrix = vuMatrix44FMultiplyMatrix(projectionMatrix, modelViewMatrix);
    renderModel(modelViewProjectionMatrix, mObjeto1VertexCount, mObjeto1Vertices.data(), mObjeto1TexCoords.data(),
                mObjeto1TextureUnit);

    VuMatrix44F model2ViewProjectionMatrix = vuMatrix44FMultiplyMatrix(projectionMatrix, modelViewMatrix);
    renderModelD(model2ViewProjectionMatrix, mObjeto2VertexCount, mObjeto2Vertices.data(), mObjeto2TexCoords.data(),
                mObjeto2TextureUnit);

    /*// Triangulo
    VuVector4F trianguloColor{ 0.63671875f, 0.76953125f, 0.22265625f, 1.0 };
    renderTriangulo(projectionMatrix, modelViewMatrix, 0.015f, trianguloColor);*/
}

void
GLESRenderer::createTexture(int width, int height, unsigned char* bytes, GLuint& textureId)
{
    if (textureId != -1)
    {
        GLESUtils::destroyTexture(textureId);
        textureId = -1;
    }

    textureId = GLESUtils::createTexture(width, height, bytes);
}


void
GLESRenderer::renderCube(const VuMatrix44F& projectionMatrix, const VuMatrix44F& modelViewMatrix, float scale, const VuVector4F& color)
{
    VuMatrix44F scaledModelViewMatrix;
    VuMatrix44F modelViewProjectionMatrix;
    VuVector3F scaleVec{ scale, scale, scale };

    scaledModelViewMatrix = vuMatrix44FScale(scaleVec, modelViewMatrix);
    modelViewProjectionMatrix = vuMatrix44FMultiplyMatrix(projectionMatrix, scaledModelViewMatrix);

    ///////////////////////////////////////////////////////////////
    // Render with const ambient diffuse light uniform color shader
    glEnable(GL_DEPTH_TEST);
    glUseProgram(mUniformColorShaderProgramID);

    glEnableVertexAttribArray(mUniformColorVertexPositionHandle);

    glVertexAttribPointer(mUniformColorVertexPositionHandle, 3, GL_FLOAT, GL_FALSE, 0, (const GLvoid*)&cubeVertices[0]);

    glUniformMatrix4fv(mUniformColorMvpMatrixHandle, 1, GL_FALSE, (GLfloat*)modelViewProjectionMatrix.data);
    glUniform4f(mUniformColorColorHandle, color.data[0], color.data[1], color.data[2], color.data[3]);

    // Draw
    glDrawElements(GL_TRIANGLES, NUM_CUBE_INDEX, GL_UNSIGNED_SHORT, (const GLvoid*)&cubeIndices[0]);

    // disable input data structures
    glDisableVertexAttribArray(mUniformColorVertexPositionHandle);
    glUseProgram(0);
    glDisable(GL_DEPTH_TEST);

    GLESUtils::checkGlError("Render cube");
    ///////////////////////////////////////////////////////
}

// Triangulo
// Metodo para renderizar el Triangulo.
float incrementoX = 0.03f;
float incrementoY = 0.0f;
float y = 0.0f;
float x = 0.0f;

void
GLESRenderer::renderTriangulo(const VuMatrix44F& projectionMatrix, const VuMatrix44F& modelViewMatrix, float scale, const VuVector4F& color)
{


    VuMatrix44F scaledModelViewMatrix;
    VuMatrix44F modelViewProjectionMatrix;
    VuVector3F scaleVec{ scale, scale, scale };
    scaledModelViewMatrix = vuMatrix44FScale(scaleVec, modelViewMatrix);

    ///////// ------- Rotacion ------- /////////
    VuVector3F vectRotacion{0,0,1};

    clock_t start = clock();

    // Se ve fluido.
    VuMatrix44F matRotacion = vuMatrix44FRotationMatrix(0.090f * start/1000, vectRotacion);

    // Se ve como reloj.
    /*long time = start/CLOCKS_PER_SEC;
    VuMatrix44F matRotacion = vuMatrix44FRotationMatrix(10.0f * time, vectRotacion);*/

    ///////// ------- FIN ------- /////////

    ///////// ------- Traslación ------- /////////

    ////////// IMPORTANTE: La inicilaización de los incrementos es imporatente a tomar en cuenta

    x += incrementoX;
    y += incrementoY;

    ///////////////////////////// Movimiento <-/->
    /*if (x > 5.0f)
    {
        incrementoX = -0.03f;
    }

    if (x < -5.0f)
    {
        incrementoX = 0.03f;
    }

    VuVector3F vectTrasformacion{x ,0,0};*/

    ////////////////////////////// Movimiento en L
    /*if (x >= 5.0f)
    {
        incrementoX = 0.0f;
        incrementoY = 0.03f;
    }

    if (y >= 5.0f)
    {
        x = 0.0f;
        y = 0.0f;
    }

    if (x <= 0)
    {
        incrementoX = 0.03f;
        incrementoY = 0.0f;
    }
    VuVector3F vectTrasformacion{x ,y,0};*/

    ////////////////////// Movimiento en U, origen (x,y) = (-5,-5)

    if (x >= 10.0f)
    {
        incrementoX = 0.0f;
        incrementoY = 0.03f;
    }

    if (x >= 10.0f && y >= 10.0f)
    {
        incrementoX = -0.03f;
        incrementoY = 0.0f;
    }

    if (x <= 0)
    {
        incrementoX = 0.03f;
        incrementoY = 0.0f;
    }

    if (x <= 0.0f && y >= 10.0f)
    {
        x = 0;
        y = 0;
    }

    VuVector3F vectTrasformacion{x-5 ,y-5,0};

// Cambio del punto de origen.
//    VuVector3F vectTrasformacion{x + 2.0f,y+ 2.0f,0};

    VuMatrix44F matTrasformacion = vuMatrix44FTranslationMatrix(vectTrasformacion);

    ///////// ------- FIN ------- /////////

    VuMatrix44F matSca_X_Tras = vuMatrix44FMultiplyMatrix(scaledModelViewMatrix, matTrasformacion);
    VuMatrix44F matSca_X_Tras_X_Rot = vuMatrix44FMultiplyMatrix(matSca_X_Tras,matRotacion);
//    modelViewProjectionMatrix = vuMatrix44FMultiplyMatrix(projectionMatrix, scaledModelViewMatrix);
    modelViewProjectionMatrix = vuMatrix44FMultiplyMatrix(projectionMatrix, matSca_X_Tras_X_Rot);

    ///////////////////////////////////////////////////////////////
    // Render with const ambient diffuse light uniform color shader
    glEnable(GL_DEPTH_TEST);
    glUseProgram(mUniformColorShaderProgramID);

    glEnableVertexAttribArray(mUniformColorVertexPositionHandle);

    glVertexAttribPointer(mUniformColorVertexPositionHandle, 3, GL_FLOAT, GL_FALSE, 0,
                          (const GLvoid *) &trianguloVertices[0]);

    glUniformMatrix4fv(mUniformColorMvpMatrixHandle, 1, GL_FALSE,
                           (GLfloat *) modelViewProjectionMatrix.data);
    glUniform4f(mUniformColorColorHandle, color.data[0], color.data[1], color.data[2],
                    color.data[3]);

    // Draw
    glDrawElements(GL_TRIANGLES, NUM_CUBE_INDEX, GL_UNSIGNED_SHORT,
                       (const GLvoid *) &trianguloIndices[0]);

    // disable input data structures
    glDisableVertexAttribArray(mUniformColorVertexPositionHandle);
    glUseProgram(0);
    glDisable(GL_DEPTH_TEST);
    GLESUtils::checkGlError("Render cube");
    ///////////////////////////////////////////////////////

}



void
GLESRenderer::renderAxis(const VuMatrix44F& projectionMatrix, const VuMatrix44F& modelViewMatrix, const VuVector3F& scale, float lineWidth)
{
    VuMatrix44F scaledModelViewMatrix;
    VuMatrix44F modelViewProjectionMatrix;

    scaledModelViewMatrix = vuMatrix44FScale(scale, modelViewMatrix);
    modelViewProjectionMatrix = vuMatrix44FMultiplyMatrix(projectionMatrix, scaledModelViewMatrix);

    ///////////////////////////////////////////////////////
    // Render with vertex color shader
    glEnable(GL_DEPTH_TEST);
    glUseProgram(mVertexColorShaderProgramID);

    glEnableVertexAttribArray(mVertexColorVertexPositionHandle);
    glVertexAttribPointer(mVertexColorVertexPositionHandle, 3, GL_FLOAT, GL_FALSE, 0, (const GLvoid*)&axisVertices[0]);

    glEnableVertexAttribArray(mVertexColorColorHandle);
    glVertexAttribPointer(mVertexColorColorHandle, 4, GL_FLOAT, GL_FALSE, 0, (const GLvoid*)&axisColors[0]);

    glUniformMatrix4fv(mVertexColorMvpMatrixHandle, 1, GL_FALSE, (GLfloat*)modelViewProjectionMatrix.data);

    // Draw
    float stateLineWidth;
    glGetFloatv(GL_LINE_WIDTH, &stateLineWidth);

    glLineWidth(lineWidth);

    glDrawElements(GL_LINES, NUM_AXIS_INDEX, GL_UNSIGNED_SHORT, (const GLvoid*)&axisIndices[0]);

    // disable input data structures
    glDisableVertexAttribArray(mVertexColorVertexPositionHandle);
    glDisableVertexAttribArray(mVertexColorColorHandle);
    glUseProgram(0);
    glDisable(GL_DEPTH_TEST);

    glLineWidth(stateLineWidth);

    GLESUtils::checkGlError("Render axis");
    ///////////////////////////////////////////////////////
}


float aumentoX = 0.0f;

void
GLESRenderer::recibirIncremento(float valor)
{
    aumentoX = valor;
}

float
GLESRenderer::getVariable() {
    return aumentoX;
}

void
GLESRenderer::renderModel(VuMatrix44F modelViewProjectionMatrix, const int numVertices, const float* vertices,
                          const float* textureCoordinates, GLuint textureId)
{
    glEnable(GL_DEPTH_TEST);
    glEnable(GL_CULL_FACE);
    glCullFace(GL_BACK);
    glFrontFace(GL_CCW);

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    glUseProgram(mTextureUniformColorShaderProgramID);

    glEnableVertexAttribArray(mTextureUniformColorVertexPositionHandle);
    glVertexAttribPointer(mTextureUniformColorVertexPositionHandle, 3, GL_FLOAT, GL_FALSE, 0, (const GLvoid*)vertices);

    glEnableVertexAttribArray(mTextureUniformColorTextureCoordHandle);
    glVertexAttribPointer(mTextureUniformColorTextureCoordHandle, 2, GL_FLOAT, GL_FALSE, 0, (const GLvoid*)textureCoordinates);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, textureId);

    //------------------------------------//
    VuVector3F vectTrasformacion{-0.1 ,0,0};
    VuMatrix44F matTraslacion = vuMatrix44FTranslationMatrix(vectTrasformacion);
    VuMatrix44F matFinal = vuMatrix44FMultiplyMatrix(modelViewProjectionMatrix, matTraslacion);
    //____________________________________//

    glUniformMatrix4fv(mTextureUniformColorMvpMatrixHandle, 1, GL_FALSE, (GLfloat*)matFinal.data);
    glUniform4f(mTextureUniformColorColorHandle, 1.0f, 1.0f, 1.0f, 1.0f);
    glUniform1i(mTextureUniformColorTexSampler2DHandle, 0); // texture unit, not handle

    // Draw
    glDrawArrays(GL_TRIANGLES, 0, numVertices);

    // disable input data structures
    glDisableVertexAttribArray(mTextureUniformColorTextureCoordHandle);
    glDisableVertexAttribArray(mTextureUniformColorVertexPositionHandle);
    glUseProgram(0);

    glBindTexture(GL_TEXTURE_2D, 0);

    GLESUtils::checkGlError("Render model");

    glDisable(GL_BLEND);
    glDisable(GL_CULL_FACE);
    glDisable(GL_DEPTH_TEST);

    LOG("Valor %f", aumentoX);
}

// Objeto 2
void
GLESRenderer::renderModelD(VuMatrix44F modelViewProjectionMatrix, const int numVertices, const float* vertices,
                          const float* textureCoordinates, GLuint textureId)
{
    glEnable(GL_DEPTH_TEST);
    glEnable(GL_CULL_FACE);
    glCullFace(GL_BACK);
    glFrontFace(GL_CCW);

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    glUseProgram(mTextureUniformColorShaderProgramID);

    glEnableVertexAttribArray(mTextureUniformColorVertexPositionHandle);
    glVertexAttribPointer(mTextureUniformColorVertexPositionHandle, 3, GL_FLOAT, GL_FALSE, 0, (const GLvoid*)vertices);

    glEnableVertexAttribArray(mTextureUniformColorTextureCoordHandle);
    glVertexAttribPointer(mTextureUniformColorTextureCoordHandle, 2, GL_FLOAT, GL_FALSE, 0, (const GLvoid*)textureCoordinates);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, textureId);

    //------------------------------------//
    VuVector3F vectTrasformacion{0.1 ,0,0};
    VuMatrix44F matTraslacion = vuMatrix44FTranslationMatrix(vectTrasformacion);
    VuMatrix44F matFinal = vuMatrix44FMultiplyMatrix(modelViewProjectionMatrix, matTraslacion);
    //____________________________________//

    //glUniformMatrix4fv(mTextureUniformColorMvpMatrixHandle, 1, GL_FALSE, (GLfloat*)modelViewProjectionMatrix.data);
    glUniformMatrix4fv(mTextureUniformColorMvpMatrixHandle, 1, GL_FALSE, (GLfloat*)matFinal.data);
    glUniform4f(mTextureUniformColorColorHandle, 1.0f, 1.0f, 1.0f, 1.0f);
    glUniform1i(mTextureUniformColorTexSampler2DHandle, 0); // texture unit, not handle

    // Draw
    glDrawArrays(GL_TRIANGLES, 0, numVertices);

    // disable input data structures
    glDisableVertexAttribArray(mTextureUniformColorTextureCoordHandle);
    glDisableVertexAttribArray(mTextureUniformColorVertexPositionHandle);
    glUseProgram(0);

    GLESUtils::checkGlError("Render model");

    glDisable(GL_BLEND);
    glDisable(GL_CULL_FACE);
    glDisable(GL_DEPTH_TEST);
}


bool
GLESRenderer::readAsset(AAssetManager* assetManager, const char* filename, std::vector<char>& data)
{
    LOG("Reading asset %s", filename);
    AAsset* asset = AAssetManager_open(assetManager, filename, AASSET_MODE_STREAMING);
    if (asset == nullptr)
    {
        LOG("Error opening asset file %s", filename);
        return false;
    }
    auto assetSize = AAsset_getLength(asset);
    data.reserve(assetSize);
    char buf[BUFSIZ];
    int nb_read = 0;
    while ((nb_read = AAsset_read(asset, buf, BUFSIZ)) > 0)
    {
        std::copy(&buf[0], &buf[BUFSIZ], std::back_inserter(data));
    }
    AAsset_close(asset);
    if (nb_read < 0)
    {
        LOG("Error reading asset file %s", filename);
        return false;
    }
    return true;
}

void
GLESRenderer::setAumentoX(float value)
{
    aumentoX = value;
}

bool
GLESRenderer::loadObjModel(const std::vector<char>& data, int& numVertices, std::vector<float>& vertices, std::vector<float>& texCoords)
{
    tinyobj::attrib_t attrib;
    std::vector<tinyobj::shape_t> shapes;
    std::vector<tinyobj::material_t> materials;

    std::string warn;
    std::string err;

    MemoryInputStream aFileDataStream(data.data(), data.size());
    bool ret = tinyobj::LoadObj(&attrib, &shapes, &materials, &warn, &err, &aFileDataStream);
    if (!ret || !err.empty())
    {
        LOG("Error loading model (%s)", err.c_str());
        return false;
    }
    if (!warn.empty())
    {
        LOG("Warning loading model (%s)", warn.c_str());
    }

    numVertices = 0;
    vertices.clear();
    texCoords.clear();
    // Loop over shapes
    // s is the index into the shapes vector
    // f is the index of the current face
    // v is the index of the current vertex
    for (size_t s = 0; s < shapes.size(); ++s)
    {
        // Loop over faces(polygon)
        size_t index_offset = 0;
        for (size_t f = 0; f < shapes[s].mesh.num_face_vertices.size(); ++f)
        {
            int fv = shapes[s].mesh.num_face_vertices[f];
            numVertices += fv;

            for (size_t v = 0; v < fv; ++v)
            {
                // access to vertex
                tinyobj::index_t idx = shapes[s].mesh.indices[index_offset + v];

                vertices.push_back(attrib.vertices[3 * idx.vertex_index + 0]);
                vertices.push_back(attrib.vertices[3 * idx.vertex_index + 1]);
                vertices.push_back(attrib.vertices[3 * idx.vertex_index + 2]);

                // The model may not have texture coordinates for every vertex
                // If a texture coordinate is missing we just set it to 0,0
                // This may not be suitable for rendering some OBJ model files
                if (idx.texcoord_index < 0)
                {
                    texCoords.push_back(0.f);
                    texCoords.push_back(0.f);
                }
                else
                {
                    texCoords.push_back(attrib.texcoords[2 * idx.texcoord_index + 0]);
                    texCoords.push_back(attrib.texcoords[2 * idx.texcoord_index + 1]);
                }
            }
            // Loop over vertices in the face.
            index_offset += fv;
        }
    }
    return true;
}

