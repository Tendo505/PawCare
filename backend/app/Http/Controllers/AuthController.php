<?php

namespace App\Http\Controllers;

use App\Models\User;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;
use Illuminate\Validation\ValidationException;
use Illuminate\Validation\Rules\Password;

class AuthController extends Controller
{
    //create an account and return its app token
    public function register(Request $request): JsonResponse
    {
        $name = $request->input('name');
        $email = $request->input('email');

        //normalize text without hiding invalid input types from validation
        $request->merge([
            'name' => is_string($name) ? trim($name) : $name,
            'email' => is_string($email) ? strtolower(trim($email)) : $email,
        ]);

        $data = $request->validate([
            'name' => ['required', 'string', 'max:100'],
            'email' => ['bail', 'required', 'string', 'max:254', 'email:rfc', 'unique:users,email'],
            'password' => ['required', 'string', Password::min(8)->letters()->numbers()],
        ]);

        //password is hashed by the user model
        $user = User::create($data);

        return response()->json([
            'token' => $user->createToken('android')->plainTextToken,
            'user' => $user,
        ], 201);
    }

    //validate login details and replace the previous app session
    public function login(Request $request): JsonResponse
    {
        $email = $request->input('email');
        $request->merge([
            'email' => is_string($email) ? strtolower(trim($email)) : $email,
        ]);

        $data = $request->validate([
            'email' => ['bail', 'required', 'string', 'max:254', 'email:rfc'],
            'password' => ['required', 'string', 'max:255'],
        ]);

        $user = User::where('email', $data['email'])->first();

        if (! $user || ! Hash::check($data['password'], $user->password))
        {
            throw ValidationException::withMessages([
                'email' => ['The provided credentials are incorrect.'],
            ]);
        }

        //keep one active app session per owner
        $user->tokens()->delete();

        return response()->json([
            'token' => $user->createToken('android')->plainTextToken,
            'user' => $user,
        ]);
    }

    //read the account linked to the current token
    public function me(Request $request): JsonResponse
    {
        return response()->json($request->user());
    }

    //delete the current access token and sign out
    public function logout(Request $request): JsonResponse
    {
        $request->user()->currentAccessToken()?->delete();

        return response()->json(['message' => 'Signed out successfully.']);
    }
}
